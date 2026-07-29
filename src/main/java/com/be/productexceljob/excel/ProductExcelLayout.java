package com.be.productexceljob.excel;

public final class ProductExcelLayout {
    public static final int KEYWORD_COLUMN_INDEX = 11; // L
    public static final int MY_CATEGORY_COLUMN_INDEX = 19; // T
    public static final int NAVER_CATEGORY_COLUMN_INDEX = 20; // U
    public static final int TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX = 26; // AA
    public static final int TOP_NAVER_CATEGORIES_START_COLUMN_INDEX = 27; // AB
    public static final int TOP_NAVER_CATEGORIES_COUNT = 5;
    public static final int SELECTED_CATEGORY_COLUMN_INDEX = 32; // AG
    public static final int LLM_STATUS_COLUMN_INDEX = 33; // AH
    public static final int CATEGORY_EMBEDDING_START_COLUMN_INDEX = 34; // AI
    public static final int CATEGORY_EMBEDDING_COUNT = 5;
    public static final int TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH = 35 * 256;
    public static final int TOP_NAVER_CATEGORY_COLUMN_WIDTH = 60 * 256;
    public static final int SELECTED_CATEGORY_COLUMN_WIDTH = 60 * 256;
    public static final int LLM_STATUS_COLUMN_WIDTH = 50 * 256;
    public static final int LLM_STATUS_DETAIL_MAX_LENGTH = 180;
    public static final int DEFAULT_KEYWORD_COUNT = 30;
    public static final String KEYWORD_HEADER = "키워드";
    public static final String MY_CATEGORY_HEADER = "마이카테";
    public static final String NAVER_CATEGORY_HEADER = "네이버카테";
    public static final String TOP_NAVER_PRODUCT_NAME_HEADER = "상품명";
    public static final String TOP_NAVER_CATEGORIES_HEADER_PREFIX = "유사상품-";
    public static final String SELECTED_CATEGORY_HEADER = "선택카테고리";
    public static final String LLM_STATUS_HEADER = "LLM상태";
    public static final String CATEGORY_EMBEDDING_HEADER_PREFIX = "카테고리검색-";

    private ProductExcelLayout() {
    }
}
