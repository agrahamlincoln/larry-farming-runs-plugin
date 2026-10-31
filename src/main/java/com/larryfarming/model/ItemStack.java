package com.larryfarming.model;

import lombok.Value;

@Value
public class ItemStack {
    int itemId;
    int quantity;
    String name;
}
