## WorldShifter

WorldShifter is a simple tool to shift chunk, including tile entities/entities and height maps on the Y axis. It's made to be used when converting a Vanilla 1.12.2 world to 1.13+, ex. with the `--forceUpgrade` when using Spigot. Hence why the entities are still in the region file. 
Beside shifting the chunks, it also deletes empty chunks or chunks filled with only air and ignores region files/out of bounds entities (after shifting).

To use the tool run the following command:

> java -jar WorldShifter-1.0-SNAPSHOT.jar <\path to world> <\offset> <br>
> **Note: Offset must be in the values of 16**

Ex. `java -jar "C:\Users\david\AppData\Roaming\.minecraft 1-20-4 Server\world" -2032`

Once ran, the tool will show the number of removed chunks/entities and the shifted region files will be in the `regionShifted` folder in the world directory.

### Attribution
- **[ens-gijs/NBT](https://github.com/ens-gijs/NBT)**: The tool uses the NBT library, provided by [ens-gijs](https://github.com/ens-gijs), so make sure to check it out