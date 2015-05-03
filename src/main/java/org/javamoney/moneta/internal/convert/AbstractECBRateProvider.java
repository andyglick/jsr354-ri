/**
 * Copyright (c) 2012, 2014, Credit Suisse (Anatole Tresch), Werner Keil and others by the @author tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.javamoney.moneta.internal.convert;

import java.io.InputStream;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversionException;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;
import javax.money.spi.Bootstrap;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.javamoney.moneta.ExchangeRateBuilder;
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.javamoney.moneta.spi.LoaderService;
import org.javamoney.moneta.spi.LoaderService.LoaderListener;

/**
 * Base to all Europe Central Bank implementation.
 *
 * @author otaviojava
 */
abstract class AbstractECBRateProvider extends AbstractRateProvider implements
        LoaderListener {

	private static final Logger LOG = Logger.getLogger(AbstractECBRateProvider.class.getName());

    private static final String BASE_CURRENCY_CODE = "EUR";

    /**
     * Base currency of the loaded rates is always EUR.
     */
    public static final CurrencyUnit BASE_CURRENCY = Monetary.getCurrency(BASE_CURRENCY_CODE);

    /**
     * Historic exchange rates, rate timestamp as UTC long.
     */
    protected final Map<LocalDate, Map<String, ExchangeRate>> rates = new ConcurrentHashMap<>();
    /**
     * Parser factory.
     */
    private final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    AbstractECBRateProvider(ProviderContext context) {
        super(context);
        saxParserFactory.setNamespaceAware(false);
        saxParserFactory.setValidating(false);
        LoaderService loader = Bootstrap.getService(LoaderService.class);
        loader.addLoaderListener(this, getDataId());
        loader.loadDataAsync(getDataId());
    }

    protected abstract String getDataId();

    @Override
    public void newDataLoaded(String resourceId, InputStream is) {
        final int oldSize = this.rates.size();
        try {
            SAXParser parser = saxParserFactory.newSAXParser();
            parser.parse(is, new RateECBReadingHandler(rates, getContext()));
        } catch (Exception e) {
        	LOG.log(Level.FINEST, "Error during data load.", e);
        }
        int newSize = this.rates.size();
        LOG.info("Loaded " + resourceId + " exchange rates for days:" + (newSize - oldSize));
    }

    protected LocalDate getQueryDates(ConversionQuery query) {
        LocalDate date = query.get(LocalDate.class);
        if (date == null) {
            LocalDateTime dateTime = query.get(LocalDateTime.class);
            if (dateTime != null) {
                date = dateTime.toLocalDate();
            }
        }
        return date;
    }

    @Override
    public ExchangeRate getExchangeRate(ConversionQuery conversionQuery) {
        Objects.requireNonNull(conversionQuery);
        if (rates.isEmpty()) {
            return null;
        }
        LocalDate date = getQueryDates(conversionQuery);
        Map<String, ExchangeRate> targets = Collections.emptyMap();
        if (date == null) {
        	Comparator<LocalDate> comparator = Comparator.naturalOrder();
        	date = this.rates.keySet().stream().sorted(comparator.reversed()).findFirst().orElseThrow(() -> new ExchangeRateException("There is not more recent exchange rate to  rate on IMFRateProvider."));
        	targets = this.rates.get(date);
        } else {
        	targets = this.rates.get(date);
        	if(targets == null) {
        		throw new ExchangeRateException("There is not recent exchange on day " + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + " to rate to  rate on IMFRateProvider.");
        	}
        }

        ExchangeRateBuilder builder = getBuilder(conversionQuery, date);
        ExchangeRate sourceRate = targets.get(conversionQuery.getBaseCurrency()
                .getCurrencyCode());
        ExchangeRate target = targets
                .get(conversionQuery.getCurrency().getCurrencyCode());
        return createExchangeRate(conversionQuery, builder, sourceRate, target);
    }

    private ExchangeRate createExchangeRate(ConversionQuery query,
                                            ExchangeRateBuilder builder, ExchangeRate sourceRate,
                                            ExchangeRate target) {

        if (areBothBaseCurrencies(query)) {
            builder.setFactor(DefaultNumberValue.ONE);
            return builder.build();
        } else if (BASE_CURRENCY_CODE.equals(query.getCurrency().getCurrencyCode())) {
            if (Objects.isNull(sourceRate)) {
                return null;
            }
            return reverse(sourceRate);
        } else if (BASE_CURRENCY_CODE.equals(query.getBaseCurrency()
                .getCurrencyCode())) {
            return target;
        } else {

            ExchangeRate rate1 = getExchangeRate(
                    query.toBuilder().setTermCurrency(Monetary.getCurrency(BASE_CURRENCY_CODE)).build());
            ExchangeRate rate2 = getExchangeRate(
                    query.toBuilder().setBaseCurrency(Monetary.getCurrency(BASE_CURRENCY_CODE))
                            .setTermCurrency(query.getCurrency()).build());
            if (Objects.nonNull(rate1) && Objects.nonNull(rate2)) {
                builder.setFactor(multiply(rate1.getFactor(), rate2.getFactor()));
                builder.setRateChain(rate1, rate2);
                return builder.build();
            }
            throw new CurrencyConversionException(query.getBaseCurrency(),
                    query.getCurrency(), sourceRate.getContext());
        }
    }

    private boolean areBothBaseCurrencies(ConversionQuery query) {
        return BASE_CURRENCY_CODE.equals(query.getBaseCurrency().getCurrencyCode()) &&
                BASE_CURRENCY_CODE.equals(query.getCurrency().getCurrencyCode());
    }


    private ExchangeRateBuilder getBuilder(ConversionQuery query, LocalDate localDate) {
        ExchangeRateBuilder builder = new ExchangeRateBuilder(
                ConversionContextBuilder.create(getContext(), RateType.HISTORIC)
                        .set(localDate).build());
        builder.setBase(query.getBaseCurrency());
        builder.setTerm(query.getCurrency());
        return builder;
    }

    private ExchangeRate reverse(ExchangeRate rate) {
        if (Objects.isNull(rate)) {
            throw new IllegalArgumentException("Rate null is not reversible.");
        }
        return new ExchangeRateBuilder(rate).setRate(rate).setBase(rate.getCurrency()).setTerm(rate.getBaseCurrency())
                .setFactor(divide(DefaultNumberValue.ONE, rate.getFactor(), MathContext.DECIMAL64)).build();
    }

}