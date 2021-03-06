package com.kingfisher.payment.api.controller;

import com.kingfisher.payment.api.database.model.CustomerRegistrationInfo;
import com.kingfisher.payment.api.database.model.TransactionLogInfo;
import com.kingfisher.payment.api.database.service.CustomerService;
import com.kingfisher.payment.api.database.service.TransactionLogService;
import com.kingfisher.payment.api.error.InputDTOValidationException;
import com.kingfisher.payment.api.model.ListRequestDTO;
import com.kingfisher.payment.api.optile.model.*;
import com.kingfisher.payment.api.optile.service.OptileService;
import com.kingfisher.payment.api.validator.groups.PaymentAPI;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.dozer.DozerBeanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@CrossOrigin
@RestController
@Api(tags = {"Payment API"}, description="Payment API")
public class PaymentController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private OptileService optileService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private TransactionLogService transactionLogService;
    @Autowired
    private DozerBeanMapper dozerBeanMapper;
    @Autowired
    private Validator validator;

    @ApiOperation(value = "Create Payment session for new transaction", nickname = "createPaymentSession")
    @ApiResponses({
            @ApiResponse(code =  422, message ="Invalid input", response = ErrorInfo.class),
            @ApiResponse(code =  401, message ="Request is not authorized, wrong authentication token or missing payment role", response = ErrorInfo.class),
            @ApiResponse(code =  500, message ="Internal server error", response = ErrorInfo.class)
    })
    @PostMapping(
            path="/session/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NetworkList> createPaymentSession(@RequestBody ListRequestDTO request) throws InputDTOValidationException {

        logger.debug("createPaymentSession(). OrderId: {}, Request Body: {}" , request.getOrderId(), request);

        Set<ConstraintViolation<ListRequestDTO>> violations = validator.validate(request, PaymentAPI.class);

        if(!violations.isEmpty()) {
            throw new InputDTOValidationException(violations);
        }

        Transaction transaction = dozerBeanMapper.map(request, Transaction.class);
        transactionLogService.populateTransactionWithOptileStaticDataAndGenerateTransactionId(transaction, request.getOrderId());

        Optional<CustomerRegistrationInfo> registrationInfo = customerService.getCustomerRegistrationInfo(request.getCustomer().getNumber());
        registrationInfo.ifPresent(regInfo -> customerService.populateRequestWithCustomerRegistrationInfo(regInfo, transaction));

        NetworkList response = optileService.postListRequest(transaction);

        transactionLogService.initAndSaveNewTransactionLogInfo(transaction, response, request.getOrderId(), registrationInfo);

        logger.debug("createPaymentSession(). OrderId: {}, Transaction: {} END." , request.getOrderId(), transaction.getTransactionId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ApiOperation(value = "Close session transaction from ATG")
    @ApiResponses({
            @ApiResponse(code =  404, message ="Not found", response = ErrorInfo.class),
            @ApiResponse(code =  400, message ="Invalid input", response = ErrorInfo.class),
            @ApiResponse(code =  503, message ="Server Internal Error", response = ErrorInfo.class)
    })
    @PostMapping(
            path = "/session/close/{profileId}/{orderId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Payout> closePaymentCharge(@PathVariable("profileId") String profileId,
                                                     @PathVariable("orderId") String orderId,
                                                     @RequestBody Transaction transaction) {

        //TODO handle case with chargeId not found
        String chargeId = "";
        Optional<TransactionLogInfo> logInfo = transactionLogService.getLatestOrderTransaction(orderId);

        if(logInfo.isPresent() && logInfo.get().getCustomerRegistrationInfo().getProfileId().equalsIgnoreCase(profileId)) {
            chargeId = logInfo.get().getChargeId();
        }

        return ResponseEntity.status(HttpStatus.OK).body(optileService.closePaymentCharge(chargeId, transaction));
    }

    @ApiOperation(value = "Charge payment from ATG", nickname = "chargePayment")
    @ApiResponses({
            @ApiResponse(code =  503, message ="Server Internal Error", response = ErrorInfo.class)
    })
    @PostMapping(
            path = "/session/charge/{orderId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Payout> chargePayment(@PathVariable("orderId") String orderId,
                                                @RequestBody String requestBody) throws InputDTOValidationException {

        if(!requestBody.equals("{}")) {
            throw new InputDTOValidationException("Request body for operation should be {}");
        }

        final AtomicReference<Payout> payoutReference = new AtomicReference<>();

        Optional<TransactionLogInfo> transactionLogInfo = transactionLogService.getLatestOrderTransaction(orderId);

        transactionLogInfo.ifPresent(trnLogInfo -> {

            Payout chargePayout = optileService.chargePayment(trnLogInfo.getListId(), requestBody);
            customerService.saveOptileDataForCustomer(chargePayout);

            if(chargePayout.getReturnCode().getName().equals("OK")) {
                trnLogInfo.setChargeId(chargePayout.getIdentification().getLongId());
                transactionLogService.saveOrUpdateTransaction(trnLogInfo);
            }

            payoutReference.set(chargePayout);

        });

        return ResponseEntity.status(HttpStatus.OK).body(payoutReference.get());
    }

    //TODO refactor like chargePayment with input parameters order or profileId or both.
    @ApiOperation(value = "Refund payment")
    @ApiResponses({
            @ApiResponse(code =  404, message ="Not found", response = ErrorInfo.class),
            @ApiResponse(code =  400, message ="Invalid input", response = ErrorInfo.class),
            @ApiResponse(code =  503, message ="Server Internal Error", response = ErrorInfo.class)
    })
    @PostMapping(
            path = "/session/refund/{chargeId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Payout> refundPayment(@PathVariable("chargeId") String chargeId,
                                                @RequestBody Operation operation) {
        return ResponseEntity.status(HttpStatus.OK).body(optileService.refundPayment(chargeId, operation));
    }

    @ApiOperation(value = "Cancel session transaction")
    @ApiResponses({
            @ApiResponse(code =  404, message ="Not found", response = ErrorInfo.class),
            @ApiResponse(code =  400, message ="Invalid input", response = ErrorInfo.class),
            @ApiResponse(code =  503, message ="Server Internal Error", response = ErrorInfo.class)
    })
    @DeleteMapping(
            path = "/session/cancel/{listId}")
    public ResponseEntity closePaymentCharge(@PathVariable("listId") String listId) {
        return ResponseEntity.status(HttpStatus.OK).build();
    }

}

