package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.config.ExportProperties;
import com.cabin.data.entity.ExportRawPayloadRow;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.data.dto.ExportCreateRequest;
import com.cabin.data.dto.ExportGroupRequest;
import com.cabin.data.dto.ExportJobResponse;
import com.cabin.data.entity.ExportJobRow;
import com.cabin.data.mapper.ExportJobMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ExportJobService {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectProvider<DataRecordMapper> dataRecordMapperProvider;
    private final ObjectProvider<ExportJobMapper> exportJobMapperProvider;
    private final ObjectMapper objectMapper;
    private final ExportProperties properties;
    private final Executor executor;

    public ExportJobService(
            ObjectProvider<DataRecordMapper> dataRecordMapperProvider,
            ObjectProvider<ExportJobMapper> exportJobMapperProvider,
            ObjectMapper objectMapper,
            ExportProperties properties,
            @Qualifier("exportTaskExecutor") Executor executor
    ) {
        this.dataRecordMapperProvider = dataRecordMapperProvider;
        this.exportJobMapperProvider = exportJobMapperProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
    }

    public List<ExportJobResponse> create(ExportCreateRequest request, CurrentUser currentUser) {
        List<ExportJobResponse> jobs = new java.util.ArrayList<>();
        Set<String> dataTypeCodes = new LinkedHashSet<>();
        for (ExportGroupRequest group : request.groups()) {
            String dataTypeCode = normalizeDataTypeCode(group.dataTypeCode());
            if (!dataTypeCodes.add(dataTypeCode)) {
                throw new BusinessException(ResponseCode.VALIDATION_ERROR, "数据类型不能重复");
            }
            List<UUID> recordIds = List.copyOf(new LinkedHashSet<>(group.recordIds()));
            ensureEnabledDataType(dataTypeCode);

            UUID jobId = UUID.randomUUID();
            exportJobMapper().insert(jobId, dataTypeCode, snapshot(dataTypeCode, recordIds), currentUser.id());
            executor.execute(() -> generate(jobId, currentUser.id(), dataTypeCode, recordIds));
            jobs.add(response(requireJob(jobId, currentUser.id())));
        }
        return List.copyOf(jobs);
    }

    public PageResponse<ExportJobResponse> list(int page, int pageSize, CurrentUser currentUser) {
        validatePage(page, pageSize);
        ExportJobMapper exportJobMapper = exportJobMapper();
        long total = exportJobMapper.countByRequestedBy(currentUser.id());
        List<ExportJobResponse> items = total == 0
                ? List.of()
                : exportJobMapper.findPage(currentUser.id(), pageSize, (page - 1) * pageSize)
                        .stream()
                        .map(this::response)
                        .toList();
        return PageResponse.of(items, page, pageSize, total);
    }

    public ExportJobResponse get(UUID jobId, CurrentUser currentUser) {
        return response(requireJob(jobId, currentUser.id()));
    }

    public DownloadFile download(UUID jobId, CurrentUser currentUser) {
        ExportJobRow job = requireJob(jobId, currentUser.id());
        if (!("SUCCEEDED".equals(job.getStatus()) || "PARTIAL".equals(job.getStatus()))) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "导出任务尚未完成");
        }
        if (job.getStoragePath() == null || job.getFileName() == null) {
            throw new BusinessException(ResponseCode.FILE_EXPIRED, "导出文件不可用");
        }

        Path storageDir = properties.storagePath();
        Path file = storageDir.resolve(job.getStoragePath()).normalize();
        if (!file.startsWith(storageDir) || !Files.isRegularFile(file)) {
            throw new BusinessException(ResponseCode.FILE_EXPIRED, "导出文件不可用");
        }
        return new DownloadFile(file, job.getFileName());
    }

    public void delete(UUID jobId, CurrentUser currentUser) {
        ExportJobRow job = requireJob(jobId, currentUser.id());
        if ("PENDING".equals(job.getStatus()) || "RUNNING".equals(job.getStatus())) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "导出任务处理中，暂不能删除");
        }
        if (job.getStoragePath() != null) {
            Path storageDir = properties.storagePath();
            Path file = storageDir.resolve(job.getStoragePath()).normalize();
            if (file.startsWith(storageDir)) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException exception) {
                    throw new BusinessException(ResponseCode.INTERNAL_ERROR, "导出文件删除失败");
                }
            }
        }
        exportJobMapper().deleteById(jobId, currentUser.id());
    }

    private void generate(UUID jobId, UUID requestedBy, String dataTypeCode, List<UUID> recordIds) {
        ExportJobMapper exportJobMapper = exportJobMapper();
        if (exportJobMapper.markRunning(jobId, requestedBy) == 0) {
            return;
        }

        Path temporaryFile = null;
        try {
            List<ExportRawPayloadRow> rows = dataRecordMapper().findExportRawPayloadsByIds(recordIds);
            if (rows.size() != recordIds.size() || rows.stream()
                    .anyMatch(row -> !dataTypeCode.equals(row.getDataTypeCode()))) {
                exportJobMapper.fail(jobId, requestedBy, "所选数据已变更或不属于指定类型");
                return;
            }
            Path storageDir = properties.storagePath();
            Files.createDirectories(storageDir);
            String fileName = displayFileName(dataTypeCode);
            String storagePath = jobId + ".csv";
            Path targetFile = storageDir.resolve(storagePath).normalize();
            temporaryFile = Files.createTempFile(storageDir, jobId + "-", ".csv.part");

            ExportResult result = writeCsv(temporaryFile, rows);
            if (result.successRows() == 0 && result.failedRows() > 0) {
                Files.deleteIfExists(temporaryFile);
                exportJobMapper.fail(jobId, requestedBy, "没有可导出的 JSON 报文");
                return;
            }

            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            temporaryFile = null;
            exportJobMapper.complete(
                    jobId,
                    requestedBy,
                    result.failedRows() == 0 ? "SUCCEEDED" : "PARTIAL",
                    fileName,
                    storagePath,
                    Files.size(targetFile),
                    result.totalRows(),
                    result.successRows(),
                    result.failedRows()
            );
        } catch (Exception exception) {
            deleteIfPresent(temporaryFile);
            exportJobMapper.fail(jobId, requestedBy, "导出文件生成失败");
        }
    }

    private ExportResult writeCsv(Path file, List<ExportRawPayloadRow> rows) throws IOException {
        int totalRows = 0;
        int successRows = 0;
        int failedRows = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("raw_json\r\n");
            for (ExportRawPayloadRow row : rows) {
                totalRows++;
                String rawPayload = row.getRawPayload();
                if (rawPayload == null || rawPayload.isBlank()) {
                    failedRows++;
                    continue;
                }
                writer.write('"');
                writer.write(rawPayload.replace("\"", "\"\""));
                writer.write("\"\r\n");
                successRows++;
            }
        }
        return new ExportResult(totalRows, successRows, failedRows);
    }

    private void ensureEnabledDataType(String dataTypeCode) {
        boolean found = dataRecordMapper().findDataTypeOptions().stream()
                .anyMatch(option -> option.getCode().equals(dataTypeCode));
        if (!found) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "数据类型不存在或未启用");
        }
    }

    private String snapshot(String dataTypeCode, List<UUID> recordIds) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("format", "CSV");
        snapshot.put("dataTypeCode", dataTypeCode);
        snapshot.put("recordIds", recordIds);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "导出条件保存失败");
        }
    }

    private ExportJobRow requireJob(UUID jobId, UUID requestedBy) {
        ExportJobRow job = exportJobMapper().findById(jobId, requestedBy);
        if (job == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        return job;
    }

    private ExportJobResponse response(ExportJobRow job) {
        return new ExportJobResponse(
                job.getId(), job.getStatus(), job.getDataTypeCode(), job.getFileName(), job.getFormat(),
                job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(), job.getCreatedAt(), job.getCompletedAt()
        );
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || (pageSize != 20 && pageSize != 50 && pageSize != 100)) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "分页参数非法");
        }
    }

    private String normalizeDataTypeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "数据类型不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String displayFileName(String dataTypeCode) {
        String prefix = switch (dataTypeCode) {
            case "QAR" -> "QAR";
            case "GROUND_TASK" -> "TASK";
            case "GROUND_TRAFFIC_RECORD" -> "TRAFFIC";
            case "GROUND_SESSION_SUMMARY" -> "SESSION";
            case "SMART_WINDOW_STATUS" -> "WINDOW";
            case "IFE_633_BEHAVIOR" -> "IFE633";
            case "IFE_COCKRELL_BEHAVIOR" -> "IFEKKRE";
            default -> dataTypeCode;
        };
        return prefix + "-" + FILE_DATE.format(OffsetDateTime.now()) + ".csv";
    }

    private void deleteIfPresent(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 失败任务的临时文件不影响业务数据，保留给部署人员排查。
        }
    }

    private DataRecordMapper dataRecordMapper() {
        DataRecordMapper mapper = dataRecordMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private ExportJobMapper exportJobMapper() {
        ExportJobMapper mapper = exportJobMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    public record DownloadFile(Path path, String fileName) {
    }

    private record ExportResult(int totalRows, int successRows, int failedRows) {
    }
}
