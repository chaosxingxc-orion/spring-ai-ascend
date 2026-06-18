package com.huawei.ascend.examples.a2a.versatileparent;

/**
 * Shared SSE response bodies for the Versatile mock, used by both the
 * standalone {@link VersatileMockServer} and the JUnit
 * {@link VersatileMockService}.
 */
final class VersatileMockSse {

    private VersatileMockSse() {}

    /** Hotels_info event + End message + end event. */
    static String hotelsInfo() {
        return sse("hotels_info", """
                {"hotels":[\
                {"city":"北京","hotel_name":"洲际国际酒店","star":"3","rating":"4.7","location_info":"近北京游乐园 · 休闲区","most_comment":"停车方便，安保到位","comment_num":"1.3万","favorite_num":"3.6万","price":"257"},\
                {"city":"北京","hotel_name":"铂尔曼中心酒店","star":"5","rating":"3.7","location_info":"近北京游乐园 · 休闲区","most_comment":"环境优美，安静舒适","comment_num":"4.5万","favorite_num":"7.9万","price":"1318"},\
                {"city":"北京","hotel_name":"皇冠假日广场酒店","star":"4","rating":"4.1","location_info":"北京风景区 · 湖景房","most_comment":"床品舒适，睡眠质量好","comment_num":"2.3万","favorite_num":"6.1万","price":"892"},\
                {"city":"北京","hotel_name":"希尔顿花园酒店","star":"5","rating":"4.5","location_info":"北京市中心 · 地铁直达","most_comment":"服务热情周到，设施完善","comment_num":"4.2万","favorite_num":"9.3万","price":"1498"},\
                {"city":"北京","hotel_name":"万豪度假村","star":"4","rating":"4.3","location_info":"近北京机场 · 商业区","most_comment":"亲子友好，设施齐全","comment_num":"3.1万","favorite_num":"5.8万","price":"756"}],\
                "index":"0","node_id":"node_123","node_type":"QA","node_name":"查询酒店列表",\
                "workflow_id":"1883913d-2330-492b-9ddd-8687b422fae2"}\
                """)
                + sseEndMessage("123") + sseEnd();
    }

    /** Hotel_book_success event with ticket + End message + end event. */
    static String hotelBookSuccess() {
        return sse("hotel_book_success", """
                {"ticket":{\
                "person_name":"李四","order_no":"H9876543210","hotel_name":"希尔顿花园酒店",\
                "star":"5","rating":"4.5","location_info":"北京市中心 · 地铁直达",\
                "price":"1498","checkin_date":"2026-03-30","checkout_date":"2026-04-03"},\
                "index":"0","node_id":"node_456","node_type":"QA","node_name":"确认酒店预订",\
                "workflow_id":"1883913d-2330-492b-9ddd-8687b422fae2"}\
                """)
                + sseEndMessage("456") + sseEnd();
    }

    /** Hotels_info WITHOUT End (for interrupt scenario). */
    static String hotelsInfoNoEnd() {
        return sse("hotels_info", """
                {"hotels":[\
                {"city":"北京","hotel_name":"洲际国际酒店","star":"3","rating":"4.7","location_info":"近北京游乐园 · 休闲区","most_comment":"停车方便，安保到位","comment_num":"1.3万","favorite_num":"3.6万","price":"257"},\
                {"city":"北京","hotel_name":"希尔顿花园酒店","star":"5","rating":"4.5","location_info":"北京市中心 · 地铁直达","most_comment":"服务热情周到，设施完善","comment_num":"4.2万","favorite_num":"9.3万","price":"1498"}],\
                "index":"0","node_id":"node_123","node_type":"QA","node_name":"查询酒店列表",\
                "workflow_id":"1883913d-2330-492b-9ddd-8687b422fae2"}\
                """);
    }

    // ── helpers ──

    private static String sse(String event, String dataJson) {
        return "data:{\"event\":\"" + event + "\",\"data\":" + dataJson + "}\n\n";
    }

    private static String sseEnd() {
        return "data:{\"event\":\"end\",\"data\":{}}\n\n";
    }

    private static String sseEndMessage(String sourceNodeId) {
        return sse("message", """
                {"text":"","summary":"","node_id":"node_end","node_type":"End",\
                "node_name":"结束","is_finished":true,\
                "workflow_id":"1883913d-2330-492b-9ddd-8687b422fae2",\
                "index":"1","source_node_id":"node_""" + sourceNodeId + "\"}");
    }
}
