package dev.sirbepy.mc.endfight.reset;

/** Callback for streaming every block in a snapshot without materialising a huge intermediate
 * list - see {@link IslandSnapshot#forEach}. */
public interface BlockVisitor {

    void visit(int x, int y, int z, String blockData);
}
