# Item and armour art sources

`build.py icons` reads these Minecraft 1.21.1 textures from the game resources jar
prepared by Gradle. Mojang Studios created the templates; the derived textures
retain their silhouettes, shading and armour UV layout, recoloured to the
Face-Stealer’s violet-grey plating. The Java/Python source licence does not
relicense Mojang’s underlying artwork. No unmodified vanilla templates are vendored.

| Output | Minecraft template |
|---|---|
| Chitin | `textures/item/armadillo_scute.png` |
| Cracked Carapace | `textures/item/turtle_scute.png` |
| Four chitin armour items | `textures/item/netherite_{helmet,chestplate,leggings,boots}.png` |
| Worn armour layers | `textures/models/armor/netherite_layer_{1,2}.png` |

The orange fissures and the Stolen Face’s stepped bone-mask sprite are authored
in the generator. The creature’s original Blockbench file by nfx remains untouched.
Sound recordings and their licences are listed separately in [sounds/SOURCES.md](sounds/SOURCES.md).
