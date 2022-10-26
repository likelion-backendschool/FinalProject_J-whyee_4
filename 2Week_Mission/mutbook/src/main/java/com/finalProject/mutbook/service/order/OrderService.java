package com.finalProject.mutbook.service.order;

import com.finalProject.mutbook.domain.cartItem.CartItem;
import com.finalProject.mutbook.domain.member.Member;
import com.finalProject.mutbook.domain.order.Order;
import com.finalProject.mutbook.domain.order.OrderItem;
import com.finalProject.mutbook.domain.order.OrderRepository;
import com.finalProject.mutbook.domain.product.Product;
import com.finalProject.mutbook.service.book.MyBookService;
import com.finalProject.mutbook.service.cartItem.CartItemService;
import com.finalProject.mutbook.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CartItemService cartItemService;
    private final MyBookService myBookService;
    private final MemberService memberService;
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Member currentMember) {
        List<CartItem> cartItems = cartItemService.findAllByBuyer(currentMember);
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product currentProduct = cartItem.getProduct();
            orderItems.add(new OrderItem(currentProduct));

            cartItemService.removeItem(currentMember.getId(), currentProduct.getId());
        }

        return create(currentMember, orderItems);
    }

    @Transactional
    public Order create(Member buyer, List<OrderItem> orderItems) {
        Order order = Order
                .builder()
                .buyer(buyer)
                .build();

        for (OrderItem orderItem : orderItems) {
            order.addOrderItem(orderItem);
        }

        // 주문 품목으로 부터 이름을 만든다.
        order.makeName();

        orderRepository.save(order);

        return order;
    }

    public void cancelOrder(Order order) {
        orderRepository.delete(order);
    }

    @Transactional
    public void refundOrder(Order order) {
        Member buyer = order.getBuyer();
        int payPrice = order.calculatePayPrice();

        // PAYPrice를 restCash로 반환
        memberService.refundCash(buyer, payPrice, "주문__%d__환불__토스페이먼츠".formatted(order.getId()));

        order.setPaymentRefund();
        orderRepository.save(order);
        myBookService.deleteBook(order);
    }

    @Transactional
    public void payByTossPayments(Order order, long useRestCash) {
        Member buyer = order.getBuyer();
        int payPrice = order.calculatePayPrice();

        long pgPayPrice = payPrice - useRestCash;
        memberService.addCash(buyer, pgPayPrice, "주문__%d__충전__토스페이먼츠".formatted(order.getId()));
        memberService.addCash(buyer, pgPayPrice * -1, "주문__%d__사용__토스페이먼츠".formatted(order.getId()));

        if ( useRestCash > 0 ) {
            memberService.addCash(buyer, useRestCash * -1, "주문__%d__사용__예치금".formatted(order.getId()));
        }

        order.setPaymentDone();
        orderRepository.save(order);
        myBookService.saveBook(order);
    }

    @Transactional
    public void payByRestCashOnly(Order order) {
        Member buyer = order.getBuyer();

        long restCash = buyer.getRestCash();

        int payPrice = order.calculatePayPrice();

        if (payPrice > restCash) {
            throw new RuntimeException("예치금이 부족합니다.");
        }

        memberService.addCash(buyer, payPrice * -1, "주문__%d__사용__예치금".formatted(order.getId()));

        order.setPaymentDone();
        orderRepository.save(order);
    }

    public boolean actorCanPayment(Member actor, Order order) {
        return actorCanSee(actor, order);
    }

    public boolean actorCanSee(Member actor, Order order) {
        return actor.getId().equals(order.getBuyer().getId());
    }

    public List<Order> findAllByBuyer(Long buyerId) {
        return orderRepository.findAllByBuyerId(buyerId);
    }

    public Order findByOrderId(Long orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }
}
