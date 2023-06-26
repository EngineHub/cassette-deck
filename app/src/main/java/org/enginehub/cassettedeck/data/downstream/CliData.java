package org.enginehub.cassettedeck.data.downstream;

import java.util.List;
import java.util.Map;

public record CliData(
    Map<String, BlockManifest> blocks,
    List<String> items,
    List<String> entities,
    List<String> biomes,
    Map<String, List<String>> blocktags,
    Map<String, List<String>> itemtags,
    Map<String, List<String>> entitytags
) {
    public record BlockManifest(String defaultstate, Map<String, BlockProperty> properties) {
    }

    public record BlockProperty(List<String> values, String type) {
    }

}
