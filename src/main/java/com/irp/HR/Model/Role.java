package com.irp.HR.Model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "roles" )
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roleName;
}

