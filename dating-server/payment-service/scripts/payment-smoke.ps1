param(
    [string]$BaseUrl = "http://localhost:18085",
    [long]$UserId = 10001,
    [string]$ProductCode = "COIN_100",
    [string]$Provider = "MOCK"
)

$ErrorActionPreference = "Stop"

function Invoke-PaymentGet {
    param(
        [string]$Path,
        [hashtable]$Query
    )

    $builder = [System.UriBuilder]::new($BaseUrl.TrimEnd("/") + $Path)
    if ($Query -and $Query.Count -gt 0) {
        $pairs = foreach ($key in $Query.Keys) {
            $value = $Query[$key]
            if ($null -ne $value) {
                "{0}={1}" -f [System.Uri]::EscapeDataString([string]$key), [System.Uri]::EscapeDataString([string]$value)
            }
        }
        $builder.Query = [string]::Join("&", $pairs)
    }

    Invoke-RestMethod -Method Get -Uri $builder.Uri.AbsoluteUri
}

function Assert-Code {
    param(
        [object]$Response,
        [int]$ExpectedCode,
        [string]$Step
    )

    if ([int]$Response.code -ne $ExpectedCode) {
        throw "$Step failed: expected code $ExpectedCode, actual code $($Response.code), message=$($Response.message)"
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$tradeNo = "smoke-$runId"
$differentTradeNo = "smoke-different-$runId"

Write-Host "payment smoke baseUrl=$BaseUrl userId=$UserId productCode=$ProductCode provider=$Provider"

$create = Invoke-PaymentGet "/internal/debug/payment/order/create" @{
    userId = $UserId
    productCode = $ProductCode
    provider = $Provider
}
Assert-Code $create 0 "create order"
Assert-True ($create.orderNo -and $create.orderNo.StartsWith("pay_")) "create order failed: orderNo missing"
Assert-True ([int]$create.status -eq 10) "create order failed: expected status 10, actual $($create.status)"
$orderNo = [string]$create.orderNo
$amountCents = [long]$create.amountCents
Write-Host "created orderNo=$orderNo amountCents=$amountCents"

$prepare = Invoke-PaymentGet "/internal/debug/payment/order/prepare" @{
    orderNo = $orderNo
    returnUrl = "https://app.local/success"
    cancelUrl = "https://app.local/cancel"
}
Assert-Code $prepare 0 "prepare payment"
Assert-True ($prepare.payUrl -and $prepare.providerPayload -and [long]$prepare.expireAtMs -gt 0) "prepare payment failed: payUrl/providerPayload/expireAtMs missing"
Write-Host "prepared payUrl=$($prepare.payUrl)"

$pay = Invoke-PaymentGet "/internal/debug/payment/order/mock-pay-success" @{
    orderNo = $orderNo
    providerTradeNo = $tradeNo
    paidAmountCents = $amountCents
    currency = "USD"
}
Assert-Code $pay 0 "mock pay success"
Assert-True ([int]$pay.status -eq 20) "mock pay success failed: expected status 20, actual $($pay.status)"
Write-Host "paid providerTradeNo=$tradeNo balance=$($pay.balance)"

$retrySame = Invoke-PaymentGet "/internal/debug/payment/order/mock-pay-success" @{
    orderNo = $orderNo
    providerTradeNo = $tradeNo
    paidAmountCents = $amountCents
    currency = "USD"
}
Assert-Code $retrySame 0 "retry same provider trade no"
Assert-True ($retrySame.message -eq "already paid") "retry same provider trade no failed: expected already paid, actual $($retrySame.message)"
Write-Host "retry same trade passed"

$retryDifferent = Invoke-PaymentGet "/internal/debug/payment/order/mock-pay-success" @{
    orderNo = $orderNo
    providerTradeNo = $differentTradeNo
    paidAmountCents = $amountCents
    currency = "USD"
}
Assert-Code $retryDifferent 4000 "retry different provider trade no"
Assert-True ($retryDifferent.message -like "*provider_trade_no*") "retry different provider trade no failed: message=$($retryDifferent.message)"
Write-Host "retry different trade rejected"

$getOrder = Invoke-PaymentGet "/internal/debug/payment/order/get" @{
    orderNo = $orderNo
}
Assert-Code $getOrder 0 "get order"
Assert-True ([int]$getOrder.status -eq 20) "get order failed: expected status 20, actual $($getOrder.status)"
Assert-True ($getOrder.providerTradeNo -eq $tradeNo) "get order failed: expected providerTradeNo $tradeNo, actual $($getOrder.providerTradeNo)"
Write-Host "get order passed"

$callbacks = Invoke-PaymentGet "/internal/debug/payment/callback-records" @{
    orderNo = $orderNo
    limit = 20
}
Assert-Code $callbacks 0 "list callback records"
Assert-True ($callbacks.records.Count -ge 3) "list callback records failed: expected at least 3 records, actual $($callbacks.records.Count)"
$successCount = @($callbacks.records | Where-Object { [int]$_.processStatus -eq 20 }).Count
$failedCount = @($callbacks.records | Where-Object { [int]$_.processStatus -eq 30 }).Count
Assert-True ($successCount -ge 2) "callback records failed: expected at least 2 success records, actual $successCount"
Assert-True ($failedCount -ge 1) "callback records failed: expected at least 1 failed record, actual $failedCount"
Write-Host "callback records passed success=$successCount failed=$failedCount"

Write-Host "payment smoke passed orderNo=$orderNo tradeNo=$tradeNo"
