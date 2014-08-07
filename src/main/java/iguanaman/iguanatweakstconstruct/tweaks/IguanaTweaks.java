package iguanaman.iguanatweakstconstruct.tweaks;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import iguanaman.iguanatweakstconstruct.leveling.modifiers.ModXpAwareRedstone;
import iguanaman.iguanatweakstconstruct.reference.Config;
import iguanaman.iguanatweakstconstruct.reference.Reference;
import iguanaman.iguanatweakstconstruct.tweaks.handlers.FlintHandler;
import iguanaman.iguanatweakstconstruct.restriction.PartRestrictionHandler;
import iguanaman.iguanatweakstconstruct.tweaks.handlers.StoneToolHandler;
import iguanaman.iguanatweakstconstruct.tweaks.handlers.VanillaToolNerfHandler;
import iguanaman.iguanatweakstconstruct.util.Log;
import iguanaman.iguanatweakstconstruct.util.RecipeRemover;
import mantle.pulsar.pulse.Handler;
import mantle.pulsar.pulse.Pulse;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.ShapedOreRecipe;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.CastingRecipe;
import tconstruct.library.crafting.ModifyBuilder;
import tconstruct.library.crafting.PatternBuilder;
import tconstruct.library.crafting.ToolBuilder;
import tconstruct.library.modifier.ItemModifier;
import tconstruct.library.util.IPattern;
import tconstruct.modifiers.tools.ModFlux;
import tconstruct.modifiers.tools.ModRedstone;
import tconstruct.smeltery.TinkerSmeltery;
import tconstruct.tools.TinkerTools;
import tconstruct.world.TinkerWorld;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Various Tweaks for Tinkers Construct and Vanilla Minecraft. See Config.
 */

@Pulse(id = Reference.PULSE_TWEAKS, description = "Various Tweaks for vanilla Minecraft and Tinker's Construct. See Config.")
public class IguanaTweaks {

    @Handler
    public void postInit(FMLPostInitializationEvent event)
    {
        // remove tinkers messages
        TinkerTools.supressMissingToolLogs = true;

        // flint recipes n stuff
        flintTweaks();

        if(Config.easyToolRepair)
            GameRegistry.addRecipe(new RepairCraftingRecipe());

        if(Config.castsBurnMaterial)
            castCreatingConsumesPart();

        if(Config.allowPartReuse)
            reusableToolParts();

        // no stone tools for you
        if(Config.disableStoneTools)
            MinecraftForge.EVENT_BUS.register(new StoneToolHandler());

        // because diamond pickaxe is hax
        if(Config.nerfVanillaTools)
            MinecraftForge.EVENT_BUS.register(new VanillaToolNerfHandler());

        // stonetorches
        if(Config.removeStoneTorchRecipe)
        {
            Log.info("Removing stone torch recipe");
            RecipeRemover.removeAnyRecipeFor(Item.getItemFromBlock(TinkerWorld.stoneTorch));
        }

        // silky jewel nerfs
        if(Config.moreExpensiveSilkyCloth)
        {
            Log.info("Making Silky Cloth more expensive");
            RecipeRemover.removeAnyRecipe(new ItemStack(TinkerTools.materials, 1, 25));
            String[] patSurround = { "###", "#m#", "###" };
            GameRegistry.addRecipe(new ItemStack(TinkerTools.materials, 1, 25), patSurround, 'm', new ItemStack(TinkerTools.materials, 1, 14), '#', new ItemStack(Items.string));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(TinkerTools.materials, 1, 25), patSurround, 'm', "ingotGold", '#', new ItemStack(Items.string)));
        }
        if(Config.moreExpensiveSilkyJewel)
        {
            Log.info("Making Silky Jewel more expensive");
            RecipeRemover.removeAnyRecipe(new ItemStack(TinkerTools.materials, 1, 26));
            GameRegistry.addRecipe(new ItemStack(TinkerTools.materials, 1, 26), " c ", "cec", " c ", 'c', new ItemStack(TinkerTools.materials, 1, 25), 'e', new ItemStack(Item.getItemFromBlock(Blocks.emerald_block)));
        }

        if(Config.moreModifiersForFlux)
            exchangeFluxModifier();
    }

    private void flintTweaks()
    {
        if(Config.removeFlintDrop) {
            Log.info("Removing Flint drops from Gravel");
            MinecraftForge.EVENT_BUS.register(new FlintHandler());
        }

        if(Config.addFlintRecipe) {
            Log.info("Adding shapeless Flint recipe from " + Config.recipeGravelPerFlint + " Gravel");
            // create recipe
            Object[] recipe = new ItemStack[Config.recipeGravelPerFlint];
            for(int i = 0; i < Config.recipeGravelPerFlint; i++)
                recipe[i] = new ItemStack(Blocks.gravel);

            // add recipe
            GameRegistry.addShapelessRecipe(new ItemStack(Items.flint), recipe);
        }
    }

    private void castCreatingConsumesPart()
    {
        Log.info("Modifying cast creation to consume toolpart");
        try {
            Field consume = CastingRecipe.class.getDeclaredField("consumeCast");
            consume.setAccessible(true);

            for(CastingRecipe recipe : TConstructRegistry.getTableCasting().getCastingRecipes())
                if(recipe.getResult().getItem() == TinkerSmeltery.metalPattern)
                    consume.set(recipe, true);
        } catch (NoSuchFieldException e) {
            Log.error("Couldn't find field to modify");
            Log.error(e);
        } catch (IllegalAccessException e) {
            Log.error("Couldn't modify casting pattern");
            Log.error(e);
        }
    }

    private void reusableToolParts() {
        Log.info("Registering reusable tool parts");
        // the material IDs of non-metal parts
        //int[] nonMetals = { 0, 1, 3, 4, 5, 6, 7, 8, 9, 17, 31 };
        for (Map.Entry<List, ItemStack> entry : TConstructRegistry.patternPartMapping.entrySet()) {
            Item pattern = (Item) entry.getKey().get(0); // the pattern
            Integer meta = (Integer) entry.getKey().get(1); // metadata of the pattern
            Integer matID = (Integer) entry.getKey().get(2); // Material-ID of the material needed to craft
            ItemStack toolPart = (ItemStack) entry.getValue(); // the itemstack created

            // get pattern cost
            int cost = ((IPattern)pattern).getPatternCost(new ItemStack(pattern, 1, meta)); // the cost is 0.5*2
            if(cost <= 0)
                continue;

            PatternBuilder.instance.registerMaterial(toolPart, cost, TConstructRegistry.getMaterial(matID).materialName);
        }
    }

    private void exchangeFluxModifier()
    {

        List<ItemModifier> mods = ModifyBuilder.instance.itemModifiers;
        for(ListIterator<ItemModifier> iter = mods.listIterator(); iter.hasNext();)
        {
            ItemModifier mod = iter.next();
            // flux mod
            if(mod instanceof ModFlux) {
                iter.set(new ModFluxExpensive(((ModFlux) mod).batteries));
                Log.trace("Replaced Flux Modifier to make it more expensive");
            }
        }
    }
}
