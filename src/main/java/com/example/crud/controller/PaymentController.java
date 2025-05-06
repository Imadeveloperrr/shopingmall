package com.example.crud.controller;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.service.PaymentService;
import com.example.crud.entity.Orders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 결제 처리 요청
    @PostMapping("/process")
    @ResponseBody
    public Map<String, Object> processPayment(@RequestBody PaymentDto paymentDto) {
        boolean success = paymentService.processPayment(paymentDto);
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", paymentDto.getOrderId());
        response.put("success", success);
        return response;
    }

    // 결제 성공 페이지
    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("orderId") Long orderId, Model model) {
        Orders order = paymentService.getOrderById(orderId);
        model.addAttribute("order", order);
        return "payment/success"; // 결제 성공 페이지 템플릿
    }

    // 결제 실패 페이지
    @GetMapping("/failure")
    public String paymentFailure(@RequestParam("orderId") Long orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "payment/failure"; // 결제 실패 페이지 템플릿
    }
}
