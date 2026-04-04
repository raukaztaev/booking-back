package org.example.bookingback.mapper;

import org.example.bookingback.dto.resource.ResourceResponse;
import org.example.bookingback.entity.Resource;
import org.springframework.stereotype.Component;

@Component
public class ResourceMapper {

    public ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getDescription(),
                resource.getCapacity(),
                resource.isRestricted(),
                resource.getOwner().getId(),
                resource.getOwner().getEmail(),
                resource.getCreatedAt()
        );
    }
}
