/*
 * This file is part of ViaBackwards - https://github.com/ViaVersion/ViaBackwards
 * Copyright (C) 2016-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viabackwards.protocol.v1_16to1_15_2.rewriter;

import com.viaversion.viabackwards.ViaBackwards;
import com.viaversion.viabackwards.api.rewriters.text.JsonNBTComponentRewriter;
import com.viaversion.viabackwards.protocol.v1_16to1_15_2.Protocol1_16To1_15_2;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonPrimitive;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.packet.ClientboundPackets1_16;
import com.viaversion.viaversion.util.ComponentUtil;
import com.viaversion.viaversion.util.SerializerVersion;
import java.util.Map;

public class TranslatableRewriter1_16 extends JsonNBTComponentRewriter<ClientboundPackets1_16> {

    private static final ChatColor[] COLORS = {
        new ChatColor("black", 0x000000),
        new ChatColor("dark_blue", 0x0000aa),
        new ChatColor("dark_green", 0x00aa00),
        new ChatColor("dark_aqua", 0x00aaaa),
        new ChatColor("dark_red", 0xaa0000),
        new ChatColor("dark_purple", 0xaa00aa),
        new ChatColor("gold", 0xffaa00),
        new ChatColor("gray", 0xaaaaaa),
        new ChatColor("dark_gray", 0x555555),
        new ChatColor("blue", 0x5555ff),
        new ChatColor("green", 0x55ff55),
        new ChatColor("aqua", 0x55ffff),
        new ChatColor("red", 0xff5555),
        new ChatColor("light_purple", 0xff55ff),
        new ChatColor("yellow", 0xffff55),
        new ChatColor("white", 0xffffff)
    };
    public TranslatableRewriter1_16(Protocol1_16To1_15_2 protocol) {
        super(protocol, ReadType.JSON);
    }

    @Override
    protected void processJsonObject(UserConnection connection, JsonObject object) {
        JsonPrimitive color = object.getAsJsonPrimitive("color");
        if (color != null) {
            String legacyColor = legacyColor(color.getAsString());
            if (legacyColor != null) {
                object.addProperty("color", legacyColor);
            }
        }

        super.processJsonObject(connection, object);

        JsonObject clickEvent = object.getAsJsonObject("clickEvent");
        if (clickEvent != null && clickEvent.has("action")) {
            String action = clickEvent.get("action").getAsString();
            if (action.equals("copy_to_clipboard")) {
                clickEvent.addProperty("action", "suggest_command");
            }
        }

        JsonObject hoverEvent = object.getAsJsonObject("hoverEvent");
        if (hoverEvent == null || !hoverEvent.has("contents")) {
            return;
        }

        // show_text as chat component json, show_entity and show_item serialized as snbt
        JsonObject convertedObject = (JsonObject) ComponentUtil.convertJson(object, SerializerVersion.V1_16, SerializerVersion.V1_15);
        object.add("hoverEvent", convertedObject.getAsJsonObject("hoverEvent"));
    }

    @Override
    protected void processCompoundTag(UserConnection connection, CompoundTag tag) {
        StringTag color = tag.getStringTag("color");
        if (color != null) {
            String legacyColor = legacyColor(color.getValue());
            if (legacyColor != null) {
                color.setValue(legacyColor);
            }
        }

        super.processCompoundTag(connection, tag);
    }

    private String legacyColor(String colorName) {
        if (!colorName.matches("(?i)#[0-9a-f]{6}")) {
            return null;
        }

        int sourceRgb = Integer.parseInt(colorName.substring(1), 16);
        Map<Integer, Integer> mappings = ViaBackwards.getConfig().customHexColorMappings();
        Integer mappedRgb = mappings.get(sourceRgb);
        if (mappedRgb == null && !mappings.isEmpty()) {
            Map.Entry<Integer, Integer> nearestMapping = getClosestCustomMapping(sourceRgb, mappings);
            mappedRgb = nearestMapping.getValue();
        }
        return getClosestChatColor(mappedRgb != null ? mappedRgb : sourceRgb);
    }

    private Map.Entry<Integer, Integer> getClosestCustomMapping(int rgb, Map<Integer, Integer> mappings) {
        Map.Entry<Integer, Integer> closest = null;
        long smallestDiff = Long.MAX_VALUE;
        for (Map.Entry<Integer, Integer> mapping : mappings.entrySet()) {
            long diff = colorDifference(rgb, mapping.getKey());
            if (diff < smallestDiff) {
                closest = mapping;
                smallestDiff = diff;
            }
        }
        return closest;
    }

    private String getClosestChatColor(int rgb) {
        ChatColor closest = null;
        long smallestDiff = Long.MAX_VALUE;

        for (ChatColor color : COLORS) {
            if (color.rgb == rgb) {
                return color.colorName;
            }

            long diff = colorDifference(rgb, color.rgb);
            if (diff < smallestDiff) {
                closest = color;
                smallestDiff = diff;
            }
        }
        return closest.colorName;
    }

    private long colorDifference(int firstRgb, int secondRgb) {
        int firstR = (firstRgb >> 16) & 0xFF;
        int firstG = (firstRgb >> 8) & 0xFF;
        int firstB = firstRgb & 0xFF;
        int secondR = (secondRgb >> 16) & 0xFF;
        int secondG = (secondRgb >> 8) & 0xFF;
        int secondB = secondRgb & 0xFF;

        int rAverage = (firstR + secondR) / 2;
        int rDiff = secondR - firstR;
        int gDiff = secondG - firstG;
        int bDiff = secondB - firstB;
        return ((long) (2 + (rAverage >> 8)) * rDiff * rDiff)
            + (4L * gDiff * gDiff)
            + ((long) (2 + ((255 - rAverage) >> 8)) * bDiff * bDiff);
    }

    private static final class ChatColor {

        private final String colorName;
        private final int rgb;
        private final int r;
        private final int g;
        private final int b;

        ChatColor(String colorName, int rgb) {
            this.colorName = colorName;
            this.rgb = rgb;
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
        }
    }
}
