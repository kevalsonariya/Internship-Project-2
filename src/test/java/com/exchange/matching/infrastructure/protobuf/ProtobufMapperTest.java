package com.exchange.matching.infrastructure.protobuf;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.MarketData;
import com.exchange.matching.domain.model.Order;
import com.exchange.matching.domain.model.PriceLevel;
import com.exchange.matching.domain.model.Trade;
import com.exchange.matching.protobuf.MarketDataProto;
import com.exchange.matching.protobuf.OrderProto;
import com.exchange.matching.protobuf.TradeProto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying ProtobufMapper bidirectional conversions.
 */
class ProtobufMapperTest {

    @Test
    @DisplayName("Should correctly convert Order domain object to Proto and back")
    void testOrderMapping() {
        Order original = new Order(
                "ORD-1001",
                "BTC/USDT",
                OrderSide.BUY,
                50000.0,
                1.5,
                0.5,
                1700000000000L,
                OrderType.LIMIT,
                OrderStatus.PARTIALLY_FILLED
        );

        OrderProto proto = ProtobufMapper.toProto(original);
        assertNotNull(proto);
        assertEquals("ORD-1001", proto.getOrderId());
        assertEquals("BTC/USDT", proto.getSymbol());
        assertEquals(50000.0, proto.getPrice());
        assertEquals(1.5, proto.getQuantity());
        assertEquals(0.5, proto.getFilledQuantity());

        Order converted = ProtobufMapper.toDomain(proto);
        assertNotNull(converted);
        assertEquals(original.getOrderId(), converted.getOrderId());
        assertEquals(original.getSymbol(), converted.getSymbol());
        assertEquals(original.getSide(), converted.getSide());
        assertEquals(original.getPrice(), converted.getPrice());
        assertEquals(original.getQuantity(), converted.getQuantity());
        assertEquals(original.getFilledQuantity(), converted.getFilledQuantity());
        assertEquals(original.getTimestamp(), converted.getTimestamp());
        assertEquals(original.getOrderType(), converted.getOrderType());
        assertEquals(original.getStatus(), converted.getStatus());
    }

    @Test
    @DisplayName("Should correctly convert Trade domain record to Proto and back")
    void testTradeMapping() {
        Trade original = new Trade(
                "TRD-5001",
                "ETH/USDT",
                "ORD-BUY-1",
                "ORD-SELL-2",
                3000.0,
                2.0,
                1700000005000L,
                "ORD-BUY-1",
                "ORD-SELL-2"
        );

        TradeProto proto = ProtobufMapper.toProto(original);
        assertNotNull(proto);
        assertEquals("TRD-5001", proto.getTradeId());
        assertEquals("ETH/USDT", proto.getSymbol());
        assertEquals(3000.0, proto.getPrice());

        Trade converted = ProtobufMapper.toDomain(proto);
        assertNotNull(converted);
        assertEquals(original, converted);
    }

    @Test
    @DisplayName("Should correctly convert MarketData domain record to Proto and back")
    void testMarketDataMapping() {
        PriceLevel bid1 = new PriceLevel(50000.0, 10.0, 3);
        PriceLevel ask1 = new PriceLevel(50050.0, 5.0, 2);

        MarketData original = new MarketData(
                "BTC/USDT",
                1700000010000L,
                List.of(bid1),
                List.of(ask1),
                50000.0,
                1500.0
        );

        MarketDataProto proto = ProtobufMapper.toProto(original);
        assertNotNull(proto);
        assertEquals("BTC/USDT", proto.getSymbol());
        assertEquals(1, proto.getBidsCount());
        assertEquals(1, proto.getAsksCount());

        MarketData converted = ProtobufMapper.toDomain(proto);
        assertNotNull(converted);
        assertEquals(original.symbol(), converted.symbol());
        assertEquals(original.timestamp(), converted.timestamp());
        assertEquals(original.bids().size(), converted.bids().size());
        assertEquals(original.asks().size(), converted.asks().size());
        assertEquals(original.lastPrice(), converted.lastPrice());
        assertEquals(original.volume24h(), converted.volume24h());
    }
}
