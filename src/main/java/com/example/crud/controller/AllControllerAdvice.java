package com.example.crud.controller;

import com.example.crud.data.category.dto.CategoryGroupDto;
import com.example.crud.data.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
@RequiredArgsConstructor
public class AllControllerAdvice {

    private final CategoryService categoryService;

    @ModelAttribute("loginCheck")
    public boolean addLoginCheck() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("categoryGroups")
    public List<CategoryGroupDto> categoryGroups() {
        return categoryService.getCategoryGroups();
    }
}
