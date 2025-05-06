package com.example.crud.repository;

import com.example.crud.entity.Orders;
import com.example.crud.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Orders, Long> {
    List<Orders> findByMemberNumberOrderByOrderDateDesc(Long memberId);
    List<Orders> findByMemberNumberAndStatusOrderByOrderDateDesc(Long memberId, OrderStatus status);
}
