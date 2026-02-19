## WorldShifter

WorldShifter is a simple multithreaded tool for Minecraft 1.8.2 - 1.21.11, to shift chunks, including tile entities/entities on the Y axis into a single or multiple (sliced) worlds. It's made to be used when converting a Vanilla 1.12.2 world to 1.13+, ex. with the `--forceUpgrade` when using Spigot. Hence, why the entities are still in the region file, but it also supports entities in the `entities` folder.

### Supported tags  
The current supported chunk root tags that get shifted are the following (others get copied over):
>- `yPos`
>- `sections`
>- `block_entities`
>- `Heightmaps`
>- `HeightMap`
>- `Entities`
>- `fluid_ticks`
>- `block_ticks`
>- `blending_data`
>- `UpgradeData`
>- `PostProcessing`
>- `ToBeTicked`
>- `LiquidsToBeTicked`

### Usage
To use the tool run the following command:

``` 
java -jar WorldShifter-1.3.8.jar <worldPath>> <offset> [minY] [maxY] [--multiWorld] [--threadCount <count>]
```
>- Replace the `<worldPath>` to the path to your world.
>- Replace the `<offset>` with your desired offset, which must be in the values of 16.
>- Replace the optional `[minY]` and `[maxY]` with the minimum y value and maximum y value of the output world. Both values must be in the values of 16. 
>- Use the optional `--multiWorld` to slice the source world into multiple stacked worlds
>- Replace the optional `[--threadCount <count>]`, the <count> with the amount of threads you wish to use. The deafult is 2. 

Ex. command:  
```
java -jar WorldShifter-1.3.8.jar "C:\Users\david\AppData\Roaming\.minecraft 1-20-4 Server\world" -2032 -2032 2032 --threadCount 4
```

> Note: If you don't specify the `[minY]` and `[maxY]`, the default world height limit depending on the `DataVersion` of the region file will be chosen.

Once ran, the tool will show the number of skipped (out-of-bounds) sections/entities and the shifted region/entities files will be in the `world-shifted` folder. If `--multiWorld` was  
enabled, the folder will contain one or more `world<index>` folders.

### Future plans
A feature that I intend to add in the near future is a GUI, where the user will be able to select individual chunks to shift, by either clicking one them or using a selection box.

### Attribution
- **[ens-gijs/NBT](https://github.com/ens-gijs/NBT)**: The tool uses a fork of the NBT library, provided by [ens-gijs](https://github.com/ens-gijs), so make sure to check it out