package org.example.bookingback.controller;

import jakarta.validation.Valid;
import org.example.bookingback.dto.resource.ResourceRequest;
import org.example.bookingback.dto.resource.ResourceResponse;
import org.example.bookingback.mapper.ResourceMapper;
import org.example.bookingback.security.UserPrincipal;
import org.example.bookingback.service.ResourceService;
import org.example.bookingback.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;
    private final UserService userService;
    private final ResourceMapper resourceMapper;

    public ResourceController(ResourceService resourceService, UserService userService, ResourceMapper resourceMapper) {
        this.resourceService = resourceService;
        this.userService = userService;
        this.resourceMapper = resourceMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(
            @Valid @RequestBody ResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return resourceMapper.toResponse(resourceService.create(request, userService.getById(principal.getId())));
    }

    @GetMapping
    public Page<ResourceResponse> list(Pageable pageable) {
        return resourceService.getAll(pageable).map(resourceMapper::toResponse);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return resourceMapper.toResponse(resourceService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResourceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return resourceMapper.toResponse(resourceService.update(id, request, userService.getById(principal.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        resourceService.delete(id);
    }
}
