/* Copyright (C) 2025 Interactive Brokers LLC. All rights reserved. This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */

package com.ib.client;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ib.client.protobuf.AccountDataEndProto;
import com.ib.client.protobuf.AccountSummaryEndProto;
import com.ib.client.protobuf.AccountSummaryProto;
import com.ib.client.protobuf.AccountUpdateMultiEndProto;
import com.ib.client.protobuf.AccountUpdateMultiProto;
import com.ib.client.protobuf.AccountUpdateTimeProto;
import com.ib.client.protobuf.AccountValueProto;
import com.ib.client.protobuf.CompletedOrderProto;
import com.ib.client.protobuf.CompletedOrdersEndProto;
import com.ib.client.protobuf.ContractDataEndProto;
import com.ib.client.protobuf.ContractDataProto;
import com.ib.client.protobuf.ErrorMessageProto;
import com.ib.client.protobuf.ExecutionDetailsEndProto;
import com.ib.client.protobuf.ExecutionDetailsProto;
import com.ib.client.protobuf.HeadTimestampProto;
import com.ib.client.protobuf.HistogramDataProto;
import com.ib.client.protobuf.HistoricalDataEndProto;
import com.ib.client.protobuf.HistoricalDataProto;
import com.ib.client.protobuf.HistoricalDataUpdateProto;
import com.ib.client.protobuf.HistoricalTicksBidAskProto;
import com.ib.client.protobuf.HistoricalTicksLastProto;
import com.ib.client.protobuf.HistoricalTicksProto;
import com.ib.client.protobuf.ManagedAccountsProto;
import com.ib.client.protobuf.MarketDataTypeProto;
import com.ib.client.protobuf.MarketDepthL2Proto;
import com.ib.client.protobuf.MarketDepthProto;
import com.ib.client.protobuf.OpenOrderProto;
import com.ib.client.protobuf.OpenOrdersEndProto;
import com.ib.client.protobuf.OrderBoundProto;
import com.ib.client.protobuf.OrderStatusProto;
import com.ib.client.protobuf.PortfolioValueProto;
import com.ib.client.protobuf.PositionEndProto;
import com.ib.client.protobuf.PositionMultiEndProto;
import com.ib.client.protobuf.PositionMultiProto;
import com.ib.client.protobuf.PositionProto;
import com.ib.client.protobuf.RealTimeBarTickProto;
import com.ib.client.protobuf.TickByTickDataProto;
import com.ib.client.protobuf.TickGenericProto;
import com.ib.client.protobuf.TickOptionComputationProto;
import com.ib.client.protobuf.TickPriceProto;
import com.ib.client.protobuf.TickReqParamsProto;
import com.ib.client.protobuf.TickSizeProto;
import com.ib.client.protobuf.TickSnapshotEndProto;
import com.ib.client.protobuf.TickStringProto;

import java.util.Set;

public interface EWrapper {
    ///////////////////////////////////////////////////////////////////////
    // Interface methods
    ///////////////////////////////////////////////////////////////////////
    void tickPrice( int tickerId, int field, double price, TickAttrib attrib);
    void tickSize( int tickerId, int field, Decimal size);
    void tickOptionComputation( int tickerId, int field, int tickAttrib, double impliedVol,
    		double delta, double optPrice, double pvDividend,
    		double gamma, double vega, double theta, double undPrice);
	void tickGeneric(int tickerId, int tickType, double value);
	void tickString(int tickerId, int tickType, String value);
	void tickEFP(int tickerId, int tickType, double basisPoints,
			String formattedBasisPoints, double impliedFuture, int holdDays,
			String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate);
    void orderStatus( int orderId, String status, Decimal filled, Decimal remaining,
            double avgFillPrice, long permId, int parentId, double lastFillPrice,
            int clientId, String whyHeld, double mktCapPrice);
    void openOrder( int orderId, Contract contract, Order order, OrderState orderState);
    void openOrderEnd();
    void updateAccountValue(String key, String value, String currency, String accountName);
    void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue,
            double averageCost, double unrealizedPNL, double realizedPNL, String accountName);
    void updateAccountTime(String timeStamp);
    void accountDownloadEnd(String accountName);
    void nextValidId( int orderId);
    void contractDetails(int reqId, ContractDetails contractDetails);
    void bondContractDetails(int reqId, ContractDetails contractDetails);
    void contractDetailsEnd(int reqId);
    void execDetails( int reqId, Contract contract, Execution execution);
    void execDetailsEnd( int reqId);
    void updateMktDepth( int tickerId, int position, int operation, int side, double price, Decimal size);
    void updateMktDepthL2( int tickerId, int position, String marketMaker, int operation,
    		int side, double price, Decimal size, boolean isSmartDepth);
    void updateNewsBulletin( int msgId, int msgType, String message, String origExchange);
    void managedAccounts( String accountsList);
    void receiveFA(int faDataType, String xml);
    void historicalData(int reqId, Bar bar);
    void scannerParameters(String xml);
    void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
    		String benchmark, String projection, String legsStr);
    void scannerDataEnd(int reqId);
    void realtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume, Decimal wap, int count);
    void currentTime(long time);
    void fundamentalData(int reqId, String data);
    void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract);
    void tickSnapshotEnd(int reqId);
    void marketDataType(int reqId, int marketDataType);
    void commissionAndFeesReport(CommissionAndFeesReport commissionAndFeesReport);
    void position(String account, Contract contract, Decimal pos, double avgCost);
    void positionEnd();
    void accountSummary(int reqId, String account, String tag, String value, String currency);
    void accountSummaryEnd(int reqId);
    void verifyMessageAPI( String apiData);
    void verifyCompleted( boolean isSuccessful, String errorText);
    void verifyAndAuthMessageAPI( String apiData, String xyzChallenge);
    void verifyAndAuthCompleted( boolean isSuccessful, String errorText);
    void displayGroupList( int reqId, String groups);
    void displayGroupUpdated( int reqId, String contractInfo);
    void error( Exception e);
    void error( String str);
    void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson);
    void connectionClosed();
    void connectAck();
    void positionMulti( int reqId, String account, String modelCode, Contract contract, Decimal pos, double avgCost);
    void positionMultiEnd( int reqId);
    void accountUpdateMulti( int reqId, String account, String modelCode, String key, String value, String currency);
    void accountUpdateMultiEnd( int reqId);
    void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId, String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes);
    void securityDefinitionOptionalParameterEnd(int reqId);
	void softDollarTiers(int reqId, SoftDollarTier[] tiers);
    void familyCodes(FamilyCode[] familyCodes);
    void symbolSamples(int reqId, ContractDescription[] contractDescriptions);
	void historicalDataEnd(int reqId, String startDateStr, String endDateStr);
    void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions);
    void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData);
	void smartComponents(int reqId, Map<Integer, Entry<String, Character>> theMap);
	void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions);
    void newsProviders(NewsProvider[] newsProviders);
    void newsArticle(int requestId, int articleType, String articleText);
    void historicalNews(int requestId, String time, String providerCode, String articleId, String headline);
    void historicalNewsEnd(int requestId, boolean hasMore);
	void headTimestamp(int reqId, String headTimestamp);
	void histogramData(int reqId, List<HistogramEntry> items);
    void historicalDataUpdate(int reqId, Bar bar);
	void rerouteMktDataReq(int reqId, int conId, String exchange);
	void rerouteMktDepthReq(int reqId, int conId, String exchange);
    void marketRule(int marketRuleId, PriceIncrement[] priceIncrements);
	void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL);
	void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value);
    void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done);
    void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done);
    void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done);
    void tickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size, TickAttribLast tickAttribLast, String exchange, String specialConditions);
    void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize, Decimal askSize, TickAttribBidAsk tickAttribBidAsk);
    void tickByTickMidPoint(int reqId, long time, double midPoint);
    void orderBound(long permId, int clientId, int orderId);
    void completedOrder(Contract contract, Order order, OrderState orderState);
    void completedOrdersEnd();
    void replaceFAEnd(int reqId, String text);
	void wshMetaData(int reqId, String dataJson);
	void wshEventData(int reqId, String dataJson);
    void historicalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone, List<HistoricalSession> sessions);
    void userInfo(int reqId, String whiteBrandingId);
    void currentTimeInMillis(long timeInMillis);
    
    // protobuf
    void orderStatusProtoBuf(OrderStatusProto.OrderStatus orderStatusProto);
    void openOrderProtoBuf(OpenOrderProto.OpenOrder openOrderProto);
    void openOrdersEndProtoBuf(OpenOrdersEndProto.OpenOrdersEnd openOrdersEndProto);
    void errorProtoBuf(ErrorMessageProto.ErrorMessage errorMessageProto);
    void execDetailsProtoBuf(ExecutionDetailsProto.ExecutionDetails executionDetailsProto);
    void execDetailsEndProtoBuf(ExecutionDetailsEndProto.ExecutionDetailsEnd executionDetailsEndProto);
    void completedOrderProtoBuf(CompletedOrderProto.CompletedOrder completedOrderProto);
    void completedOrdersEndProtoBuf(CompletedOrdersEndProto.CompletedOrdersEnd completedOrdersEndProto);
    void orderBoundProtoBuf(OrderBoundProto.OrderBound orderBoundProto);
    void contractDataProtoBuf(ContractDataProto.ContractData contractDataProto);
    void bondContractDataProtoBuf(ContractDataProto.ContractData contractDataProto);
    void contractDataEndProtoBuf(ContractDataEndProto.ContractDataEnd contractDataEndProto);
    void tickPriceProtoBuf(TickPriceProto.TickPrice tickPriceProto);
    void tickSizeProtoBuf(TickSizeProto.TickSize tickSizeProto);
    void tickOptionComputationProtoBuf(TickOptionComputationProto.TickOptionComputation tickOptionComputationProto);
    void tickGenericProtoBuf(TickGenericProto.TickGeneric tickGenericProto);
    void tickStringProtoBuf(TickStringProto.TickString tickStringProto);
    void tickSnapshotEndProtoBuf(TickSnapshotEndProto.TickSnapshotEnd tickSnapshotEndProto);
    void updateMarketDepthProtoBuf(MarketDepthProto.MarketDepth marketDepthProto);
    void updateMarketDepthL2ProtoBuf(MarketDepthL2Proto.MarketDepthL2 marketDepthL2Proto);
    void marketDataTypeProtoBuf(MarketDataTypeProto.MarketDataType marketDataTypeProto);
    void tickReqParamsProtoBuf(TickReqParamsProto.TickReqParams tickReqParamsProto);
    void updateAccountValueProtoBuf(AccountValueProto.AccountValue accounValueProto);
    void updatePortfolioProtoBuf(PortfolioValueProto.PortfolioValue portfolioValueProto);
    void updateAccountTimeProtoBuf(AccountUpdateTimeProto.AccountUpdateTime accountUpdateTimeProto);
    void accountDataEndProtoBuf(AccountDataEndProto.AccountDataEnd accountDataEndProto);
    void managedAccountsProtoBuf(ManagedAccountsProto.ManagedAccounts managedAccountsProto);
    void positionProtoBuf(PositionProto.Position positionProto);
    void positionEndProtoBuf(PositionEndProto.PositionEnd positionEndProto);
    void accountSummaryProtoBuf(AccountSummaryProto.AccountSummary accountSummaryProto);
    void accountSummaryEndProtoBuf(AccountSummaryEndProto.AccountSummaryEnd accountSummaryEndProto);
    void positionMultiProtoBuf(PositionMultiProto.PositionMulti positionMultiProto);
    void positionMultiEndProtoBuf(PositionMultiEndProto.PositionMultiEnd positionMultiEndProto);
    void accountUpdateMultiProtoBuf(AccountUpdateMultiProto.AccountUpdateMulti accountUpdateMultiProto);
    void accountUpdateMultiEndProtoBuf(AccountUpdateMultiEndProto.AccountUpdateMultiEnd accountUpdateMultiEndProto);
    void historicalDataProtoBuf(HistoricalDataProto.HistoricalData historicalDataProto);
    void historicalDataUpdateProtoBuf(HistoricalDataUpdateProto.HistoricalDataUpdate historicalDataUpdateProto);
    void historicalDataEndProtoBuf(HistoricalDataEndProto.HistoricalDataEnd historicalDataEndProto);
    void realTimeBarTickProtoBuf(RealTimeBarTickProto.RealTimeBarTick realTimeBarTickProto);
    void headTimestampProtoBuf(HeadTimestampProto.HeadTimestamp headTimestampProto);
    void histogramDataProtoBuf(HistogramDataProto.HistogramData histogramDataProto);
    void historicalTicksProtoBuf(HistoricalTicksProto.HistoricalTicks historicalTicksProto);
    void historicalTicksBidAskProtoBuf(HistoricalTicksBidAskProto.HistoricalTicksBidAsk historicalTicksBidAskProto);
    void historicalTicksLastProtoBuf(HistoricalTicksLastProto.HistoricalTicksLast historicalTicksLastProto);
    void tickByTickDataProtoBuf(TickByTickDataProto.TickByTickData tickByTickDataProto);
}

