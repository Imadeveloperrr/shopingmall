package com.example.crud.data.category.service;

import com.example.crud.data.category.dto.CategoryGroupDto;

import java.util.List;


public interface CategoryService {
    List<CategoryGroupDto> getCategoryGroups();
}
