package com.aurora.dating.payment.service;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ListProductsResult {

    private final int code;
    private final String message;
    private final List<ProductItemResult> products;
}
