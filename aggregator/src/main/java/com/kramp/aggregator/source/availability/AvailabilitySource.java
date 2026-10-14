package com.kramp.aggregator.source.availability;

import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.source.UpstreamHttp;
import org.springframework.web.client.RestClient;

/** Stock and delivery promise for the market's warehouse. */
public final class AvailabilitySource implements ProductDataSource<StockInfo> {

  public static final String NAME = "availability";
  public static final String NO_STOCK_RECORD = "NO_STOCK_RECORD";

  private final RestClient client;

  public AvailabilitySource(RestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Criticality criticality() {
    return Criticality.OPTIONAL;
  }

  @Override
  public StockInfo fetch(ProductRequest request) {
    AvailabilityResponse body =
        UpstreamHttp.fetch(
            NAME,
            () ->
                client
                    .get()
                    .uri(
                        "/availability/stock/{id}?market={market}",
                        request.productId(),
                        request.market().code())
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        (req, response) -> {
                          throw new UpstreamNotFoundException(NAME, NO_STOCK_RECORD);
                        })
                    .body(AvailabilityResponse.class));
    return toStock(body);
  }

  private static StockInfo toStock(AvailabilityResponse body) {
    StockInfo.Warehouse warehouse = body.warehouse();
    if (body.stockLevel() == null
        || body.stockLevel() < 0
        || warehouse == null
        || warehouse.code() == null
        || warehouse.city() == null
        || warehouse.country() == null
        || body.expectedDelivery() == null) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    return new StockInfo(body.stockLevel(), warehouse, body.expectedDelivery());
  }
}
