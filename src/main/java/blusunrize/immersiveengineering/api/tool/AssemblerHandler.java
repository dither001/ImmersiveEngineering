/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.api.tool;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.common.util.FakePlayerUtil;
import blusunrize.immersiveengineering.common.util.Utils.InventoryCraftingFalse;
import com.google.common.base.Preconditions;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.item.crafting.ShapelessRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * @author BluSunrize - 01.09.2016
 */
public class AssemblerHandler
{
	private static final HashMap<Class<? extends IRecipe>, IRecipeAdapter> registry = new LinkedHashMap<>();
	private static final List<Function<Object, RecipeQuery>> specialQueryConverters = new ArrayList<>();
	public static final IRecipeAdapter<IRecipe<CraftingInventory>> defaultAdapter = new IRecipeAdapter<IRecipe<CraftingInventory>>()
	{
		@Override
		public RecipeQuery[] getQueriedInputs(IRecipe<CraftingInventory> recipe, NonNullList<ItemStack> input)
		{
			NonNullList<Ingredient> ingred = recipe.getIngredients();
			CraftingInventory verificationInv = InventoryCraftingFalse.createFilledCraftingInventory(3, 3, input);
			boolean matches;
			ForgeHooks.setCraftingPlayer(FakePlayerUtil.getAnyFakePlayer());
			if(recipe instanceof IShapedRecipe)
			{
				IShapedRecipe shapedInput = (IShapedRecipe)recipe;
				ShapedRecipe verify = new ShapedRecipe(new ResourceLocation(ImmersiveEngineering.MODID, "temp"),
						"temp", shapedInput.getRecipeWidth(), shapedInput.getRecipeHeight(),
						ingred, new ItemStack(Items.GUNPOWDER));
				matches = verify.matches(verificationInv, null);
			}
			else
			{
				ShapelessRecipe verify = new ShapelessRecipe(new ResourceLocation(ImmersiveEngineering.MODID, "temp"),
						"temp", new ItemStack(Blocks.DIRT), ingred);
				matches = verify.matches(verificationInv, null);
			}
			ForgeHooks.setCraftingPlayer(null);
			if(!matches)
				return null;
			RecipeQuery[] query = new RecipeQuery[ingred.size()];
			for(int i = 0; i < query.length; i++)
			{
				query[i] = AssemblerHandler.createQuery(ingred.get(i));
			}
			return query;
		}
	};

	static
	{
		AssemblerHandler.registerRecipeAdapter(IRecipe.class, defaultAdapter);
	}

	public static void registerRecipeAdapter(Class<? extends IRecipe> recipeClass, IRecipeAdapter adapter)
	{
		registry.put(recipeClass, adapter);
	}

	@Nonnull
	public static IRecipeAdapter<?> findAdapterForClass(Class<? extends IRecipe> recipeClass)
	{
		IRecipeAdapter adapter = registry.get(recipeClass);
		boolean isSuperIRecipe = IRecipe.class.isAssignableFrom(recipeClass.getSuperclass());
		if(adapter==null&&recipeClass!=IRecipe.class&&isSuperIRecipe)
			adapter = findAdapterForClass((Class<? extends IRecipe>)recipeClass.getSuperclass());
		else
			adapter = defaultAdapter;
		registry.put(recipeClass, adapter);
		return adapter;
	}

	@Nonnull
	public static IRecipeAdapter<?> findAdapter(IRecipe recipe)
	{
		return findAdapterForClass(recipe.getClass());
	}

	public static void registerSpecialQueryConverters(Function<Object, RecipeQuery> func)
	{
		specialQueryConverters.add(func);
	}

	public interface IRecipeAdapter<R extends IRecipe<CraftingInventory>>
	{
		@Nullable
		default RecipeQuery[] getQueriedInputs(R recipe)
		{
			return getQueriedInputs(recipe, NonNullList.create());
		}

		@Nullable
		default RecipeQuery[] getQueriedInputs(R recipe, NonNullList<ItemStack> input)
		{
			return getQueriedInputs(recipe);
		}
	}

	public static RecipeQuery createQuery(Object o)
	{
		if(o==null)
			return null;
		for(Function<Object, RecipeQuery> func : specialQueryConverters)
		{
			RecipeQuery q = func.apply(o);
			if(q!=null)
				return q;
		}
		if(o instanceof ItemStack)
			return createQueryFromItemStack((ItemStack)o);
		else if(o instanceof Ingredient)
		{
			ItemStack[] stacks = ((Ingredient)o).getMatchingStacks();
			if(stacks.length <= 0)
				return null;
			if(stacks.length==1)
				return createQueryFromItemStack(stacks[0]);
			return new RecipeQuery(stacks, 1);
		}
		return new RecipeQuery(o, 1);
	}

	public static RecipeQuery createQueryFromItemStack(ItemStack stack)
	{
		FluidStack fluidStack = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
		if(!fluidStack.isEmpty())
			return new RecipeQuery(fluidStack, stack.getCount());
		return new RecipeQuery(stack, stack.getCount());
	}

	public static class RecipeQuery
	{
		public Object query;
		public int querySize;

		/**
		 * Valid types of Query are ItemStack, ItemStack[], ArrayList<ItemStack>, IngredientWithSize, String (OreDict Name) and FluidStack
		 */
		public RecipeQuery(Object query, int querySize)
		{
			Preconditions.checkArgument(
					query instanceof ItemStack||
							query instanceof ItemStack[]||
							query instanceof List||
							query instanceof IngredientWithSize||
							query instanceof ResourceLocation||
							query instanceof FluidStack,
					query+" is not a valid ingredient!"
			);
			this.query = query;
			this.querySize = querySize;
		}
	}
}
