package com.huawei.ascend.service.session.store.redis;

import com.huawei.ascend.service.session.model.Session;

public interface SessionCodec {
    String encode(Session session);

    Session decode(String value);
}
