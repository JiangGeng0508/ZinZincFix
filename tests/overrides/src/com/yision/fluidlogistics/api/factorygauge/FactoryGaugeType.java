package com.yision.fluidlogistics.api.factorygauge;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
public record FactoryGaugeType(Supplier<Item> item) { }
