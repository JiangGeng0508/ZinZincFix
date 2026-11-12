package net.liukrast.deployer.lib.logistics.board;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import net.minecraft.world.item.Item;
public class AbstractPanelBehaviour extends FactoryPanelBehaviour {
    private final Item item;
    public AbstractPanelBehaviour(Item item) { this.item = item; }
    public Item getItem() { return item; }
}
