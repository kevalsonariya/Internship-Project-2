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
import com.exchange.matching.protobuf.OrderSideProto;
import com.exchange.matching.protobuf.OrderStatusProto;
import com.exchange.matching.protobuf.OrderTypeProto;
import com.exchange.matching.protobuf.PriceLevelProto;
import com.exchange.matching.protobuf.TradeProto;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe mapper utility for converting between internal Domain models
 * and external Protocol Buffer serialization objects.
 */
public final class ProtobufMapper {

    private ProtobufMapper() {
        // Private constructor to enforce utility pattern
    }

    /**
     * Converts a domain {@link Order} entity to a {@link OrderProto} message.
     *
     * @param order domain order
     * @return protobuf order proto message
     */
    public static OrderProto toProto(Order order) {
        if (order == null) {
            return null;
        }
        return OrderProto.newBuilder()
                .setOrderId(order.getOrderId() != null ? order.getOrderId() : "")
                .setSymbol(order.getSymbol() != null ? order.getSymbol() : "")
                .setSide(toProto(order.getSide()))
                .setPrice(order.getPrice())
                .setQuantity(order.getQuantity())
                .setFilledQuantity(order.getFilledQuantity())
                .setTimestamp(order.getTimestamp())
                .setOrderType(toProto(order.getOrderType()))
                .setStatus(toProto(order.getStatus()))
                .build();
    }

    /**
     * Converts a {@link OrderProto} message to a domain {@link Order} entity.
     *
     * @param proto protobuf order proto message
     * @return domain order entity
     */
    public static Order toDomain(OrderProto proto) {
        if (proto == null) {
            return null;
        }
        return new Order(
                proto.getOrderId(),
                proto.getSymbol(),
                toDomain(proto.getSide()),
                proto.getPrice(),
                proto.getQuantity(),
                proto.getFilledQuantity(),
                proto.getTimestamp(),
                toDomain(proto.getOrderType()),
                toDomain(proto.getStatus())
        );
    }

    /**
     * Converts a domain {@link Trade} record to a {@link TradeProto} message.
     *
     * @param trade domain trade record
     * @return protobuf trade proto message
     */
    public static TradeProto toProto(Trade trade) {
        if (trade == null) {
            return null;
        }
        return TradeProto.newBuilder()
                .setTradeId(trade.tradeId())
                .setSymbol(trade.symbol())
                .setBuyOrderId(trade.buyOrderId())
                .setSellOrderId(trade.sellOrderId())
                .setPrice(trade.price())
                .setQuantity(trade.quantity())
                .setTimestamp(trade.timestamp())
                .setMakerOrderId(trade.makerOrderId())
                .setTakerOrderId(trade.takerOrderId())
                .build();
    }

    /**
     * Converts a {@link TradeProto} message to a domain {@link Trade} record.
     *
     * @param proto protobuf trade proto message
     * @return domain trade record
     */
    public static Trade toDomain(TradeProto proto) {
        if (proto == null) {
            return null;
        }
        return new Trade(
                proto.getTradeId(),
                proto.getSymbol(),
                proto.getBuyOrderId(),
                proto.getSellOrderId(),
                proto.getPrice(),
                proto.getQuantity(),
                proto.getTimestamp(),
                proto.getMakerOrderId(),
                proto.getTakerOrderId()
        );
    }

    /**
     * Converts a domain {@link PriceLevel} record to a {@link PriceLevelProto} message.
     *
     * @param priceLevel domain price level record
     * @return protobuf price level proto message
     */
    public static PriceLevelProto toProto(PriceLevel priceLevel) {
        if (priceLevel == null) {
            return null;
        }
        return PriceLevelProto.newBuilder()
                .setPrice(priceLevel.price())
                .setQuantity(priceLevel.quantity())
                .setOrderCount(priceLevel.orderCount())
                .build();
    }

    /**
     * Converts a {@link PriceLevelProto} message to a domain {@link PriceLevel} record.
     *
     * @param proto protobuf price level proto message
     * @return domain price level record
     */
    public static PriceLevel toDomain(PriceLevelProto proto) {
        if (proto == null) {
            return null;
        }
        return new PriceLevel(
                proto.getPrice(),
                proto.getQuantity(),
                proto.getOrderCount()
        );
    }

    /**
     * Converts a domain {@link MarketData} record to a {@link MarketDataProto} message.
     *
     * @param marketData domain market data record
     * @return protobuf market data proto message
     */
    public static MarketDataProto toProto(MarketData marketData) {
        if (marketData == null) {
            return null;
        }
        MarketDataProto.Builder builder = MarketDataProto.newBuilder()
                .setSymbol(marketData.symbol())
                .setTimestamp(marketData.timestamp())
                .setLastPrice(marketData.lastPrice())
                .setVolume24H(marketData.volume24h());

        if (marketData.bids() != null) {
            for (PriceLevel bid : marketData.bids()) {
                builder.addBids(toProto(bid));
            }
        }
        if (marketData.asks() != null) {
            for (PriceLevel ask : marketData.asks()) {
                builder.addAsks(toProto(ask));
            }
        }

        return builder.build();
    }

    /**
     * Converts a {@link MarketDataProto} message to a domain {@link MarketData} record.
     *
     * @param proto protobuf market data proto message
     * @return domain market data record
     */
    public static MarketData toDomain(MarketDataProto proto) {
        if (proto == null) {
            return null;
        }
        List<PriceLevel> bids = new ArrayList<>();
        for (PriceLevelProto bidProto : proto.getBidsList()) {
            bids.add(toDomain(bidProto));
        }

        List<PriceLevel> asks = new ArrayList<>();
        for (PriceLevelProto askProto : proto.getAsksList()) {
            asks.add(toDomain(askProto));
        }

        return new MarketData(
                proto.getSymbol(),
                proto.getTimestamp(),
                bids,
                asks,
                proto.getLastPrice(),
                proto.getVolume24H()
        );
    }

    /**
     * Maps domain {@link OrderSide} to {@link OrderSideProto}.
     *
     * @param side domain order side
     * @return protobuf order side
     */
    public static OrderSideProto toProto(OrderSide side) {
        if (side == null) return OrderSideProto.ORDER_SIDE_UNSPECIFIED;
        return switch (side) {
            case BUY -> OrderSideProto.BUY;
            case SELL -> OrderSideProto.SELL;
        };
    }

    /**
     * Maps {@link OrderSideProto} to domain {@link OrderSide}.
     *
     * @param proto protobuf order side
     * @return domain order side
     */
    public static OrderSide toDomain(OrderSideProto proto) {
        if (proto == null) return OrderSide.BUY;
        return switch (proto) {
            case BUY -> OrderSide.BUY;
            case SELL -> OrderSide.SELL;
            default -> OrderSide.BUY;
        };
    }

    /**
     * Maps domain {@link OrderType} to {@link OrderTypeProto}.
     *
     * @param type domain order type
     * @return protobuf order type
     */
    public static OrderTypeProto toProto(OrderType type) {
        if (type == null) return OrderTypeProto.ORDER_TYPE_UNSPECIFIED;
        return switch (type) {
            case LIMIT -> OrderTypeProto.LIMIT;
            case MARKET -> OrderTypeProto.MARKET;
        };
    }

    /**
     * Maps {@link OrderTypeProto} to domain {@link OrderType}.
     *
     * @param proto protobuf order type
     * @return domain order type
     */
    public static OrderType toDomain(OrderTypeProto proto) {
        if (proto == null) return OrderType.LIMIT;
        return switch (proto) {
            case LIMIT -> OrderType.LIMIT;
            case MARKET -> OrderType.MARKET;
            default -> OrderType.LIMIT;
        };
    }

    /**
     * Maps domain {@link OrderStatus} to {@link OrderStatusProto}.
     *
     * @param status domain order status
     * @return protobuf order status
     */
    public static OrderStatusProto toProto(OrderStatus status) {
        if (status == null) return OrderStatusProto.ORDER_STATUS_UNSPECIFIED;
        return switch (status) {
            case NEW -> OrderStatusProto.NEW;
            case PARTIALLY_FILLED -> OrderStatusProto.PARTIALLY_FILLED;
            case FILLED -> OrderStatusProto.FILLED;
            case CANCELLED -> OrderStatusProto.CANCELLED;
            case REJECTED -> OrderStatusProto.REJECTED;
        };
    }

    /**
     * Maps {@link OrderStatusProto} to domain {@link OrderStatus}.
     *
     * @param proto protobuf order status
     * @return domain order status
     */
    public static OrderStatus toDomain(OrderStatusProto proto) {
        if (proto == null) return OrderStatus.NEW;
        return switch (proto) {
            case NEW -> OrderStatus.NEW;
            case PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED;
            case FILLED -> OrderStatus.FILLED;
            case CANCELLED -> OrderStatus.CANCELLED;
            case REJECTED -> OrderStatus.REJECTED;
            default -> OrderStatus.NEW;
        };
    }
}
