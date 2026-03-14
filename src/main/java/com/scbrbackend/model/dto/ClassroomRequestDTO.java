package com.scbrbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassroomRequestDTO {
    private Long id;
    @JsonProperty("room_no")
    private String roomNo;
    private String name;
    private String location;
    private Integer capacity;
    private Integer status;
}
