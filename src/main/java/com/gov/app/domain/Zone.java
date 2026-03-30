package com.gov.app.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("zones")
public class Zone {

    @Id
    private Integer id;

    private String name;

    private String category;

    // geog is write-only via raw SQL; not mapped here (same pattern as TaxiPosition.geog)
}
