package us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.entities.Entity1_13Types;
import us.myles.ViaVersion.api.minecraft.Position;
import us.myles.ViaVersion.api.platform.providers.ViaProviders;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.remapper.PacketHandler;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.remapper.ValueCreator;
import us.myles.ViaVersion.api.remapper.ValueTransformer;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.packets.State;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.storage.ClientWorld;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.data.MappingData;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.packets.EntityPackets;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.packets.InventoryPackets;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.packets.WorldPackets;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.providers.BlockEntityProvider;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.providers.PaintingProvider;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.storage.BlockStorage;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.storage.EntityTracker;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.storage.TabCompleteTracker;
import us.myles.ViaVersion.protocols.protocolsnapshotto1_12_2.types.Particle1_13Type;

import java.util.Map;

// Development of 1.13 support!
public class ProtocolSnapshotTo1_12_2 extends Protocol {
    public static final Particle1_13Type PARTICLE_TYPE = new Particle1_13Type();

    public static String legacyTextToJson(String legacyText) {
        return ComponentSerializer.toString(
                TextComponent.fromLegacyText(legacyText)
        );
    }

    static {
        MappingData.init();
    }

    public static String jsonTextToLegacy(String value) {
        return TextComponent.toLegacyText(ComponentSerializer.parse(value));
    }

    @Override
    protected void registerPackets() {
        // Register grouped packet changes
        EntityPackets.register(this);
        WorldPackets.register(this);
        InventoryPackets.register(this);

        // Outgoing packets

        // New packet 0x0 - Login Plugin Message
        registerOutgoing(State.LOGIN, 0x0, 0x1);
        registerOutgoing(State.LOGIN, 0x1, 0x2);
        registerOutgoing(State.LOGIN, 0x2, 0x3);
        registerOutgoing(State.LOGIN, 0x3, 0x4);

        // Statistics
        registerOutgoing(State.PLAY, 0x07, 0x07, new PacketRemapper() {
            @Override
            public void registerMap() {
                // TODO: This packet has changed

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        wrapper.cancel();
                    }
                });
            }
        });

        registerOutgoing(State.PLAY, 0xF, 0xE);

        // Tab-Complete
        registerOutgoing(State.PLAY, 0xE, 0x10, new PacketRemapper() {
            @Override
            public void registerMap() {
                create(new ValueCreator() {
                    @Override
                    public void write(PacketWrapper wrapper) throws Exception {
                        wrapper.write(Type.VAR_INT, wrapper.user().get(TabCompleteTracker.class).getTransactionId());

                        String input = wrapper.user().get(TabCompleteTracker.class).getInput();
                        // Start & End
                        int index;
                        int length;
                        // If no input or new word (then it's the start)
                        if (input.endsWith(" ") || input.length() == 0) {
                            index = input.length();
                            length = 0;
                        } else {
                            // Otherwise find the last space (+1 as we include it)
                            int lastSpace = input.lastIndexOf(" ") + 1;
                            index = lastSpace;
                            length = input.length() - lastSpace;
                        }
                        // Write index + length
                        wrapper.write(Type.VAR_INT, index);
                        wrapper.write(Type.VAR_INT, length);

                        int count = wrapper.passthrough(Type.VAR_INT);
                        for (int i = 0; i < count; i++) {
                            String suggestion = wrapper.read(Type.STRING);
                            // If we're at the start then handle removing slash
                            if (suggestion.startsWith("/") && index == 0) {
                                suggestion = suggestion.substring(1);
                            }
                            wrapper.write(Type.STRING, suggestion);
                            wrapper.write(Type.BOOLEAN, false);
                        }
                    }
                });
            }
        });

        // New packet 0x11, declare commands
        registerOutgoing(State.PLAY, 0x11, 0x12);
        registerOutgoing(State.PLAY, 0x12, 0x13);
        registerOutgoing(State.PLAY, 0x13, 0x14);

        registerOutgoing(State.PLAY, 0x15, 0x16);

        registerOutgoing(State.PLAY, 0x17, 0x18);

        registerOutgoing(State.PLAY, 0x1A, 0x1B);
        registerOutgoing(State.PLAY, 0x1B, 0x1C);
        registerOutgoing(State.PLAY, 0x1C, 0x1D);
        registerOutgoing(State.PLAY, 0x1D, 0x1E);
        registerOutgoing(State.PLAY, 0x1E, 0x1F);
        registerOutgoing(State.PLAY, 0x1F, 0x20);

        registerOutgoing(State.PLAY, 0x21, 0x22);

        // Join (save dimension id)
        registerOutgoing(State.PLAY, 0x23, 0x24, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // 0 - Entity ID
                map(Type.UNSIGNED_BYTE); // 1 - Gamemode
                map(Type.INT); // 2 - Dimension

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        // Store the player
                        int entityId = wrapper.get(Type.INT, 0);
                        wrapper.user().get(EntityTracker.class).addEntity(entityId, Entity1_13Types.EntityType.PLAYER);

                        ClientWorld clientChunks = wrapper.user().get(ClientWorld.class);
                        int dimensionId = wrapper.get(Type.INT, 1);
                        clientChunks.setEnvironment(dimensionId);

                        // Send fake declare commands
                        wrapper.create(0x11, new ValueCreator() {
                            @Override
                            public void write(PacketWrapper wrapper) {
                                wrapper.write(Type.VAR_INT, 2); // Size
                                // Write root node
                                wrapper.write(Type.VAR_INT, 0); // Mark as command
                                wrapper.write(Type.VAR_INT, 1); // 1 child
                                wrapper.write(Type.VAR_INT, 1); // Child is at 1

                                // Write arg node
                                wrapper.write(Type.VAR_INT, 0x02 | 0x04 | 0x10); // Mark as command
                                wrapper.write(Type.VAR_INT, 0); // No children
                                // Extra data
                                wrapper.write(Type.STRING, "args"); // Arg name
                                wrapper.write(Type.STRING, "brigadier:string");
                                wrapper.write(Type.VAR_INT, 2); // Greedy
                                wrapper.write(Type.STRING, "minecraft:ask_server"); // Ask server

                                wrapper.write(Type.VAR_INT, 0); // Root node index
                            }
                        }).send(ProtocolSnapshotTo1_12_2.class);

                        // Send tags packet
                        wrapper.create(0x54, new ValueCreator() {
                            @Override
                            public void write(PacketWrapper wrapper) throws Exception {
                                wrapper.write(Type.VAR_INT, MappingData.blockTags.size()); // block tags
                                for (Map.Entry<String, int[]> tag : MappingData.blockTags.entrySet()) {
                                    wrapper.write(Type.STRING, tag.getKey());
                                    wrapper.write(Type.VAR_INT, tag.getValue().length);
                                    for (int id : tag.getValue()) {
                                        wrapper.write(Type.VAR_INT, id);
                                    }
                                }
                                wrapper.write(Type.VAR_INT, MappingData.itemTags.size()); // item tags
                                for (Map.Entry<String, int[]> tag : MappingData.itemTags.entrySet()) {
                                    wrapper.write(Type.STRING, tag.getKey());
                                    wrapper.write(Type.VAR_INT, tag.getValue().length);
                                    for (int id : tag.getValue()) {
                                        wrapper.write(Type.VAR_INT, id);
                                    }
                                }
                                wrapper.write(Type.VAR_INT, MappingData.fluidTags.size()); // fluid tags
                                for (Map.Entry<String, int[]> tag : MappingData.fluidTags.entrySet()) {
                                    wrapper.write(Type.STRING, tag.getKey());
                                    wrapper.write(Type.VAR_INT, tag.getValue().length);
                                    for (int id : tag.getValue()) {
                                        wrapper.write(Type.VAR_INT, id);
                                    }
                                }
                            }
                        }).send(ProtocolSnapshotTo1_12_2.class);
                    }
                });
            }
        });

        // Map packet
        registerOutgoing(State.PLAY, 0x24, 0x25, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Map id
                map(Type.BYTE); // 1 - Scale
                map(Type.BOOLEAN); // 2 - Tracking Position
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int iconCount = wrapper.passthrough(Type.VAR_INT);
                        for (int i = 0; i < iconCount; i++) {
                            byte directionAndType = wrapper.read(Type.BYTE);
                            int type = (directionAndType & 0xF0) >> 4;
                            wrapper.write(Type.VAR_INT, type);
                            wrapper.passthrough(Type.BYTE); // Icon X
                            wrapper.passthrough(Type.BYTE); // Icon Z
                            byte direction = (byte) (directionAndType & 0x0F);
                            wrapper.write(Type.BYTE, direction);
                            wrapper.write(Type.OPTIONAL_CHAT, null); // Display Name
                        }
                    }
                });
            }
        });
        registerOutgoing(State.PLAY, 0x25, 0x26);
        registerOutgoing(State.PLAY, 0x26, 0x27);
        registerOutgoing(State.PLAY, 0x27, 0x28);
        registerOutgoing(State.PLAY, 0x28, 0x29);
        registerOutgoing(State.PLAY, 0x29, 0x2A);
        registerOutgoing(State.PLAY, 0x2A, 0x2B);
        // Craft recipe response
        registerOutgoing(State.PLAY, 0x2B, 0x2C, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        // TODO This packet changed
                        wrapper.cancel();
                    }
                });
            }
        });
        registerOutgoing(State.PLAY, 0x2C, 0x2D);
        registerOutgoing(State.PLAY, 0x2D, 0x2E);
        registerOutgoing(State.PLAY, 0x2E, 0x2F);
        registerOutgoing(State.PLAY, 0x2F, 0x31);
        registerOutgoing(State.PLAY, 0x30, 0x32);
        // Recipe
        registerOutgoing(State.PLAY, 0x31, 0x33, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        // TODO: This has changed >.>
                        wrapper.cancel();
                    }
                });
            }
        });

        registerOutgoing(State.PLAY, 0x33, 0x35);
        registerOutgoing(State.PLAY, 0x34, 0x36);

        // Respawn (save dimension id)
        registerOutgoing(State.PLAY, 0x35, 0x37, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // 0 - Dimension ID
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        ClientWorld clientWorld = wrapper.user().get(ClientWorld.class);
                        int dimensionId = wrapper.get(Type.INT, 0);
                        clientWorld.setEnvironment(dimensionId);
                    }
                });
            }
        });

        registerOutgoing(State.PLAY, 0x36, 0x38);
        registerOutgoing(State.PLAY, 0x37, 0x39);
        registerOutgoing(State.PLAY, 0x38, 0x3A);
        registerOutgoing(State.PLAY, 0x39, 0x3B);
        registerOutgoing(State.PLAY, 0x3A, 0x3C);
        registerOutgoing(State.PLAY, 0x3B, 0x3D);

        registerOutgoing(State.PLAY, 0x3D, 0x3F);
        registerOutgoing(State.PLAY, 0x3E, 0x40);

        registerOutgoing(State.PLAY, 0x40, 0x42);
        registerOutgoing(State.PLAY, 0x41, 0x43);
        // Scoreboard Objective
        registerOutgoing(State.PLAY, 0x42, 0x44, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.STRING); // 0 - Objective name
                map(Type.BYTE); // 1 - Mode
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        byte mode = wrapper.get(Type.BYTE, 0);
                        // On create or update
                        if (mode == 0 || mode == 2) {
                            wrapper.passthrough(Type.STRING); // Value
                            String type = wrapper.read(Type.STRING);
                            // integer or hearts
                            wrapper.write(Type.VAR_INT, type.equals("integer") ? 0 : 1);
                        }
                    }
                });
            }
        });

        registerOutgoing(State.PLAY, 0x43, 0x45);
        // Team packet
        registerOutgoing(State.PLAY, 0x44, 0x46, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.STRING); // 0 - Team Name
                map(Type.BYTE); // 1 - Mode

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        byte action = wrapper.get(Type.BYTE, 0);

                        if (action == 0 || action == 2) {
                            wrapper.passthrough(Type.STRING); // Display Name

                            String prefix = wrapper.read(Type.STRING); // Prefix moved
                            String suffix = wrapper.read(Type.STRING); // Suffix moved

                            wrapper.passthrough(Type.BYTE); // Flags

                            wrapper.passthrough(Type.STRING); // Name Tag Visibility
                            wrapper.passthrough(Type.STRING); // Collision rule

                            // Handle new colors
                            byte color = wrapper.read(Type.BYTE);

                            if (color == -1) // -1 changed to 21
                                wrapper.write(Type.VAR_INT, 21); // RESET
                            else
                                wrapper.write(Type.VAR_INT, (int) color);

                            wrapper.write(Type.STRING, legacyTextToJson(prefix)); // Prefix
                            wrapper.write(Type.STRING, legacyTextToJson(suffix)); // Suffix
                        }
                    }
                });

            }
        });
        registerOutgoing(State.PLAY, 0x45, 0x47);
        registerOutgoing(State.PLAY, 0x46, 0x48);
        registerOutgoing(State.PLAY, 0x47, 0x49);
        registerOutgoing(State.PLAY, 0x48, 0x4A);

        // Sound Effect packet
        registerOutgoing(State.PLAY, 0x49, 0x4C, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Sound ID

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int soundId = wrapper.get(Type.VAR_INT, 0);
                        wrapper.set(Type.VAR_INT, 0, getNewSoundID(soundId));
                    }
                });
            }
        });
        registerOutgoing(State.PLAY, 0x4A, 0x4D);
        registerOutgoing(State.PLAY, 0x4B, 0x4E);
        registerOutgoing(State.PLAY, 0x4C, 0x4F);
        // Advancements
        registerOutgoing(State.PLAY, 0x4D, 0x50, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        // TODO Temporary cancel advancements because of 'Non [a-z0-9/._-] character in path of location: minecraft:? https://fs.matsv.nl/media?id=auwje4z4lxw.png
                        wrapper.cancel();
                    }
                });
            }
        });
        registerOutgoing(State.PLAY, 0x4E, 0x51);
        registerOutgoing(State.PLAY, 0x4F, 0x52);
        // New packet 0x52 - Declare Recipes
        // New packet 0x53 - Tags

        // Incoming packets

        // New packet 0x0 - Login Plugin Message
        registerIncoming(State.LOGIN, -1, 0x0, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        wrapper.cancel();
                    }
                });
            }
        });
        registerIncoming(State.LOGIN, 0x0, 0x1);
        registerIncoming(State.LOGIN, 0x1, 0x2);

        registerIncoming(State.PLAY, 0x2, 0x1);
        registerIncoming(State.PLAY, 0x3, 0x2);
        registerIncoming(State.PLAY, 0x4, 0x3);

        // Tab-Complete
        registerIncoming(State.PLAY, 0x1, 0x4, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int tid = wrapper.read(Type.VAR_INT);
                        // Save transaction id
                        wrapper.user().get(TabCompleteTracker.class).setTransactionId(tid);
                    }
                });
                // Prepend /
                map(Type.STRING, new ValueTransformer<String, String>(Type.STRING) {
                    @Override
                    public String transform(PacketWrapper wrapper, String inputValue) {
                        wrapper.user().get(TabCompleteTracker.class).setInput(inputValue);
                        return "/" + inputValue;
                    }
                });
                // Fake the end of the packet
                create(new ValueCreator() {
                    @Override
                    public void write(PacketWrapper wrapper) {
                        wrapper.write(Type.BOOLEAN, false);
                        wrapper.write(Type.OPTIONAL_POSITION, null);
                    }
                });
            }
        });

        // New 0x0A - Edit book
        registerIncoming(State.PLAY, 0x0b, 0x0c);
        registerIncoming(State.PLAY, 0x0A, 0x0B);
        registerIncoming(State.PLAY, 0x0c, 0x0d);
        registerIncoming(State.PLAY, 0x0d, 0x0e);
        registerIncoming(State.PLAY, 0x0e, 0x0f);
        registerIncoming(State.PLAY, 0x0f, 0x10);
        registerIncoming(State.PLAY, 0x10, 0x11);
        registerIncoming(State.PLAY, 0x11, 0x12);
        // New 0x13 - Pick Item
        registerIncoming(State.PLAY, -1, 0x13, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int slot = wrapper.read(Type.VAR_INT);
                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|PickItem");
                        wrapper.write(Type.VAR_INT, slot);
                    }
                });
            }
        });

        // Craft recipe request
        registerIncoming(State.PLAY, 0x12, 0x14, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        // TODO: This has changed >.>
                        wrapper.cancel();
                    }
                });
            }
        });

        registerIncoming(State.PLAY, 0x13, 0x15);
        registerIncoming(State.PLAY, 0x14, 0x16);
        registerIncoming(State.PLAY, 0x15, 0x17);
        registerIncoming(State.PLAY, 0x16, 0x18);
        registerIncoming(State.PLAY, 0x17, 0x19);

        // New 0x1A - Name Item
        registerIncoming(State.PLAY, -1, 0x1A, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        String name = wrapper.read(Type.STRING);
                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|ItemName");
                        wrapper.write(Type.STRING, name);
                    }
                });
            }
        });

        registerIncoming(State.PLAY, 0x18, 0x1B);
        registerIncoming(State.PLAY, 0x19, 0x1C);

        // New 0x1D - Select Trade
        registerIncoming(State.PLAY, -1, 0x1D, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int slot = wrapper.read(Type.INT);
                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|TrSel");
                        wrapper.write(Type.VAR_INT, slot);
                    }
                });
            }
        });
        // New 0x1E - Set Beacon Effect
        registerIncoming(State.PLAY, -1, 0x1E, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int potion = wrapper.read(Type.INT);
                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|Beacon");
                        wrapper.write(Type.VAR_INT, potion);
                    }
                });
            }
        });

        registerIncoming(State.PLAY, 0x1A, 0x1F);

        // New 0x20 - Update Command Block
        registerIncoming(State.PLAY, -1, 0x20, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Position position = wrapper.read(Type.POSITION);
                        String command = wrapper.read(Type.STRING);
                        int mode = wrapper.read(Type.VAR_INT);
                        byte flags = wrapper.read(Type.BYTE);

                        String stringMode = mode == 0 ? "SEQUENCE"
                                : mode == 1 ? "AUTO"
                                : "REDSTONE";

                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|AutoCmd");
                        wrapper.write(Type.INT, position.getX().intValue());
                        wrapper.write(Type.INT, position.getY().intValue());
                        wrapper.write(Type.INT, position.getZ().intValue());
                        wrapper.write(Type.STRING, command);
                        wrapper.write(Type.BOOLEAN, (flags & 0x1) != 0); // Track output
                        wrapper.write(Type.STRING, stringMode);
                        wrapper.write(Type.BOOLEAN, (flags & 0x2) != 0); // Is conditional
                        wrapper.write(Type.BOOLEAN, (flags & 0x4) != 0); // Automatic
                    }
                });
            }
        });
        // New 0x21 - Update Command Block Minecart
        registerIncoming(State.PLAY, -1, 0x21, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int entityId = wrapper.read(Type.INT);
                        String command = wrapper.read(Type.STRING);
                        boolean trackOutput = wrapper.read(Type.BOOLEAN);

                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|AdvCmd");
                        wrapper.write(Type.VAR_INT, entityId);
                        wrapper.write(Type.STRING, command);
                        wrapper.write(Type.BOOLEAN, trackOutput);
                    }
                });
            }
        });

        // 0x1B -> 0x22 in InventoryPackets

        // New 0x23 - Update Structure Block
        registerIncoming(State.PLAY, -1, 0x23, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Position pos = wrapper.read(Type.POSITION);
                        int action = wrapper.read(Type.VAR_INT);
                        int mode = wrapper.read(Type.VAR_INT);
                        String name = wrapper.read(Type.STRING);
                        byte offsetX = wrapper.read(Type.BYTE);
                        byte offsetY = wrapper.read(Type.BYTE);
                        byte offsetZ = wrapper.read(Type.BYTE);
                        byte sizeX = wrapper.read(Type.BYTE);
                        byte sizeY = wrapper.read(Type.BYTE);
                        byte sizeZ = wrapper.read(Type.BYTE);
                        int mirror = wrapper.read(Type.VAR_INT);
                        int rotation = wrapper.read(Type.VAR_INT);
                        String metadata = wrapper.read(Type.STRING);
                        float integrity = wrapper.read(Type.FLOAT);
                        long seed = wrapper.read(Type.VAR_LONG);
                        byte flags = wrapper.read(Type.BYTE);

                        String stringMode = mode == 0 ? "SAVE"
                                : mode == 1 ? "LOAD"
                                : mode == 2 ? "CORNER"
                                : "DATA";
                        String stringMirror = mirror == 0 ? "NONE"
                                : mirror == 1 ? "LEFT_RIGHT"
                                : "FRONT_BACK";
                        String stringRotation = mirror == 0 ? "NONE"
                                : mirror == 1 ? "CLOCKWISE_90"
                                : mirror == 2 ? "CLOCKWISE_180"
                                : "COUNTERCLOCKWISE_90";

                        wrapper.clearPacket();
                        wrapper.setId(0x09); // Plugin Message
                        wrapper.write(Type.STRING, "MC|Struct");
                        wrapper.write(Type.INT, pos.getX().intValue());
                        wrapper.write(Type.INT, pos.getY().intValue());
                        wrapper.write(Type.INT, pos.getZ().intValue());
                        wrapper.write(Type.BYTE, (byte) (action + 1));
                        wrapper.write(Type.STRING, stringMode);
                        wrapper.write(Type.STRING, name);
                        wrapper.write(Type.INT, (int) offsetX);
                        wrapper.write(Type.INT, (int) offsetY);
                        wrapper.write(Type.INT, (int) offsetZ);
                        wrapper.write(Type.INT, (int) sizeX);
                        wrapper.write(Type.INT, (int) sizeY);
                        wrapper.write(Type.INT, (int) sizeZ);
                        wrapper.write(Type.STRING, stringMirror);
                        wrapper.write(Type.STRING, stringRotation);
                        wrapper.write(Type.STRING, metadata);
                        wrapper.write(Type.BOOLEAN, (flags & 0x1) != 0); // Ignore Entities
                        wrapper.write(Type.BOOLEAN, (flags & 0x2) != 0); // Show air
                        wrapper.write(Type.BOOLEAN, (flags & 0x4) != 0); // Show bounding box
                        wrapper.write(Type.FLOAT, integrity);
                        wrapper.write(Type.VAR_LONG, seed);
                    }
                });
            }
        });

        registerIncoming(State.PLAY, 0x1C, 0x24);
        registerIncoming(State.PLAY, 0x1D, 0x25);
        registerIncoming(State.PLAY, 0x1E, 0x26);
        registerIncoming(State.PLAY, 0x1F, 0x27);



        // Recipe Book Data
        registerIncoming(State.PLAY, 0x17, 0x17, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Type

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int type = wrapper.get(Type.VAR_INT, 0);

                        if (type == 1) {
                            wrapper.passthrough(Type.BOOLEAN); // Crafting Recipe Book Open
                            wrapper.passthrough(Type.BOOLEAN); // Crafting Recipe Filter Active
                            wrapper.read(Type.BOOLEAN); // Smelting Recipe Book Open | IGNORE NEW 1.13 FIELD
                            wrapper.read(Type.BOOLEAN); // Smelting Recipe Filter Active | IGNORE NEW 1.13 FIELD
                        }
                    }
                });
            }
        });
    }

    @Override
    public void init(UserConnection userConnection) {
        userConnection.put(new EntityTracker(userConnection));
        userConnection.put(new TabCompleteTracker(userConnection));
        if (!userConnection.has(ClientWorld.class))
            userConnection.put(new ClientWorld(userConnection));
        userConnection.put(new BlockStorage(userConnection));
    }

    @Override
    protected void register(ViaProviders providers) {
        providers.register(BlockEntityProvider.class, new BlockEntityProvider());
        providers.register(PaintingProvider.class, new PaintingProvider());
    }

    // Generated with PAaaS
    private int getNewSoundID(final int oldID) {
        int newId = oldID;
        if (oldID >= 1)
            newId += 6;
        if (oldID >= 9)
            newId += 4;
        if (oldID >= 10)
            newId += 5;
        if (oldID >= 21)
            newId += 5;
        if (oldID >= 86)
            newId += 1;
        if (oldID >= 166)
            newId += 4;
        if (oldID >= 174)
            newId += 10;
        if (oldID >= 179)
            newId += 9;
        if (oldID >= 226)
            newId += 1;
        if (oldID >= 271)
            newId += 1;
        if (oldID >= 326)
            newId += 1;
        if (oldID >= 335)
            newId += 1;
        if (oldID >= 352)
            newId += 6;
        if (oldID >= 373)
            newId += 1;
        if (oldID >= 380)
            newId += 7;
        if (oldID >= 385)
            newId += 4;
        if (oldID >= 412)
            newId += 5;
        if (oldID >= 438)
            newId += 1;
        if (oldID >= 443)
            newId += 16;
        if (oldID >= 484)
            newId += 1;
        if (oldID >= 485)
            newId += 1;
        if (oldID >= 508)
            newId += 2;
        if (oldID >= 513)
            newId += 1;
        if (oldID >= 515)
            newId += 1;
        if (oldID >= 524)
            newId += 8;
        if (oldID >= 531)
            newId += 1;
        return newId;
    }
}
