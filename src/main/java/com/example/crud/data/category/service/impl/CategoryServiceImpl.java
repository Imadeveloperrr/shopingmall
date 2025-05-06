package com.example.crud.data.category.service.impl;

import com.example.crud.data.category.dto.CategoryDto;
import com.example.crud.data.category.dto.CategoryGroupDto;
import com.example.crud.data.category.service.CategoryService;
import com.example.crud.enums.Category;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Override
    public List<CategoryGroupDto> getCategoryGroups() {
        List<CategoryGroupDto> groups = new ArrayList<>();
        for (Category category : Category.values()) {
            CategoryGroupDto groupDto = new CategoryGroupDto();
            groupDto.setGroupName(category.getGroupName());
            List<CategoryDto> categoryLists = Arrays.stream(category.getSubCategories())
                    .map(name -> new CategoryDto(name, name))
                    .collect(Collectors.toList());
            groupDto.setCategoryList(categoryLists);
            groups.add(groupDto);
        }
        return groups;
    }
}
