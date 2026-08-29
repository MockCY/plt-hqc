package com.qinglian.fitness.auth;

public interface WechatGateway {

    WechatIdentity exchangeLoginCode(String loginCode);

    WechatPhone exchangePhoneCode(String phoneCode);
}
