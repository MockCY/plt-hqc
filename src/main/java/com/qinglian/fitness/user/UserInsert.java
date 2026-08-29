package com.qinglian.fitness.user;

public class UserInsert {

    private Long id;
    private final String openId;
    private final String unionId;

    public UserInsert(String openId, String unionId) {
        this.openId = openId;
        this.unionId = unionId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenId() {
        return openId;
    }

    public String getUnionId() {
        return unionId;
    }
}
