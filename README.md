## WorldShifter

WorldShifter is a simple multithreaded tool for Minecraft 1.8.2 - 1.21.11, to shift chunks, including tile entities/entities and height maps on the Y axis. It's made to be used when converting a Vanilla 1.12.2 world to 1.13+, ex. with the `--forceUpgrade` when using Spigot. Hence, why the entities are still in the region file, but it also supports entities in the `entities` folder.  
Beside shifting the chunks, it also deletes empty chunks or chunks filled with only air and ignores out of bounds entities (after shifting). 

To use the tool run the following command:

``` 
java -jar WorldShifter-1.2.6.jar <worldPath>> <offset> [minY] [maxY] [--threadCount <count>]
```
>- Replace the `<worldPath>` to the path to your world.
>- Replace the `<offset>` with your desired offset, which must be in the values of 16.
>- Replace the optional `[minY]` and `[maxY]` with the minimum y value and maximum y value of the output world. Both values must be in the values of 16. 
>- Replace the optional `[--threadCount <count>]`, the <count> with the amount of threads you wish to use. The deafult is 2. 

Ex. command:  
```
java -jar WorldShifter-1.2.6.jar "C:\Users\david\AppData\Roaming\.minecraft 1-20-4 Server\world" -2032 -2032 2032 --threadCount 4
```

> Note: If you don't specify the `[minY]` and `[maxY]`, the default world height limit depending on the `DataVersion` of the region file will be chosen.  
> **The tool also doesn't support yet altering the `PostProcessing` tag, only the `UpgradeData` tag.**

Once ran, the tool will show the number of removed chunks/entities and the shifted region/entities files will be in the `regionShifted` and `entitesShifted` folder in the world directory.

### Future plans
A feature that I intend to add in the near future is a GUI, where the user will be able to select individual chunks to shift, by either clicking one them or using a selection box. Another planned feature that is yet to be implemented is the support for the `PostProcessing` tag in terrain chunks.

### Attribution
- **[ens-gijs/NBT](https://github.com/ens-gijs/NBT)**: The tool uses the NBT library, provided by [ens-gijs](https://github.com/ens-gijs), so make sure to check it out