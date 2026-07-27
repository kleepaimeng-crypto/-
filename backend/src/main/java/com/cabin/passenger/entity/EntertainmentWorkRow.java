package com.cabin.passenger.entity;

public class EntertainmentWorkRow {
    private Long id;
    private String workCode;
    private String category;
    private String title;
    private String summary;
    private String creatorName;
    private String collectionName;
    private Integer durationSeconds;
    private Integer releaseYear;
    private String language;
    private String region;
    private Integer sortOrder;
    private String genresText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorkCode() { return workCode; }
    public void setWorkCode(String workCode) { this.workCode = workCode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getGenresText() { return genresText; }
    public void setGenresText(String genresText) { this.genresText = genresText; }
}
