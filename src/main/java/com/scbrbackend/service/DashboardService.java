package com.scbrbackend.service;

import com.scbrbackend.model.dto.dashboard.DashboardOverviewDTO;
import com.scbrbackend.model.entity.Teacher;

public interface DashboardService {
    DashboardOverviewDTO getOverview(Teacher teacher, int days);
}
