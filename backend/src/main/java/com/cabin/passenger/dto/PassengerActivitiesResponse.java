package com.cabin.passenger.dto;

import java.util.List;

public record PassengerActivitiesResponse(int total, List<PassengerActivityResponse> items) {
}
