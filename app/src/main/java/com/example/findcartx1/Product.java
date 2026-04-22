package com.example.findcartx1;

public class Product {
    public String  name;
    public String  price;
    public double  priceValue;
    public String  rackId;
    public String  floor;
    public String  category;
    public boolean isOutOfStock;
    public boolean isSelected = false;

    public Product(String name, String price, double priceValue,
                   String rackId, String floor, boolean isOutOfStock, String category) {
        this.name         = name;
        this.price        = price;
        this.priceValue   = priceValue;
        this.rackId       = rackId;
        this.floor        = floor;
        this.isOutOfStock = isOutOfStock;
        this.category     = category;
    }
}