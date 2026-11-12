package com.yision.fluidlogistics.content.logistics.factoryGauge;
import java.util.Optional;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.yision.fluidlogistics.api.factorygauge.FactoryGaugeType;
public class ResourceFactoryPanelBehaviour extends FactoryPanelBehaviour {
    public FactoryGaugeType type;
    public boolean resource = true;
    public boolean isResourceGauge() { return resource; }
    public Optional<FactoryGaugeType> registeredType() { return Optional.ofNullable(type); }
}
