package com.example.crud.enums;

public enum Category {
    // 상위 그룹: 의류 관련
    OUTER("아우터", new String[]{"패딩", "코트", "자켓", "가디건", "블레이저", "플리스/뽀글이"}),
    TOP("상의", new String[]{"니트/스웨터", "후드/맨투맨", "티셔츠", "셔츠/블라우스", "민소매/나시"}),
    BOTTOM("하의", new String[]{"청바지", "면바지", "슬랙스", "레깅스", "트레이닝", "숏팬츠"}),
    DRESS("원피스/스커트", new String[]{"미니원피스", "미디원피스", "롱원피스", "미니스커트", "미디스커트", "롱스커트"}),
    BAG("가방", new String[]{"백팩", "크로스백", "숄더백", "토트백", "클러치"}),
    SHOES("신발", new String[]{"운동화", "구두", "부츠", "샌들", "슬리퍼"}),
    ACCESSORY("악세서리", new String[]{"목걸이", "귀걸이", "반지", "팔찌", "모자", "스카프", "벨트"});

    private final String groupName;
    private final String[] subCategories;

    Category(String groupName, String[] subCategories) {
        this.groupName = groupName;
        this.subCategories = subCategories;
    }

    public String getGroupName() {
        return groupName;
    }

    public String[] getSubCategories() {
        return subCategories;
    }

    // 추가: groupName을 이용하여 Enum 객체를 반환하는 메서드
    public static Category fromGroupName(String groupName) {
        for (Category category : values()) {
            if (category.getGroupName().equalsIgnoreCase(groupName)) {
                return category;
            }
        }
        throw new IllegalArgumentException("No matching category found for: " + groupName);
    }

}
