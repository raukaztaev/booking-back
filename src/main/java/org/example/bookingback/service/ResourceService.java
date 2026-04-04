package org.example.bookingback.service;

import org.example.bookingback.dto.resource.ResourceRequest;
import org.example.bookingback.entity.Resource;
import org.example.bookingback.entity.User;
import org.example.bookingback.exception.NotFoundException;
import org.example.bookingback.repository.ResourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public Resource create(ResourceRequest request, User owner) {
        Resource resource = new Resource();
        apply(resource, request, owner);
        return resourceRepository.save(resource);
    }

    @Transactional(readOnly = true)
    public Page<Resource> getAll(Pageable pageable) {
        return resourceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Resource getById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource not found"));
    }

    public Resource update(Long id, ResourceRequest request, User owner) {
        Resource resource = getById(id);
        apply(resource, request, owner);
        return resource;
    }

    public void delete(Long id) {
        Resource resource = getById(id);
        resourceRepository.delete(resource);
    }

    private void apply(Resource resource, ResourceRequest request, User owner) {
        resource.setName(request.name());
        resource.setDescription(request.description());
        resource.setCapacity(request.capacity());
        resource.setRestricted(request.restricted());
        resource.setOwner(owner);
    }
}
