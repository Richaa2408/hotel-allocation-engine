package com.hotel.engine.algorithm.baseline;

import java.util.Arrays;
import java.util.List;

/**
 * Baseline for comparison with max flow: go through guests in order and give each
 * the first suitable room that is still free. Never revisits an earlier choice,
 * so it can leave guests unplaced even when a full arrangement exists.
 *
 * <p>Time O(G + E), where E is the total number of suitable (guest, room) pairs.
 */
public final class FirstFitAssigner {

    private FirstFitAssigner() {
    }

    /**
     * @param suitableRooms suitableRooms.get(g) = room indexes guest g can take, in preference order
     * @param roomCount     number of rooms
     * @return roomOf[g] = room index given to guest g, or -1 if none was free
     */
    public static int[] assign(List<List<Integer>> suitableRooms, int roomCount) {
        boolean[] taken = new boolean[roomCount];
        int[] roomOf = new int[suitableRooms.size()];
        Arrays.fill(roomOf, -1);
        for (int g = 0; g < suitableRooms.size(); g++) {
            for (int r : suitableRooms.get(g)) {
                if (!taken[r]) {
                    taken[r] = true;
                    roomOf[g] = r;
                    break;
                }
            }
        }
        return roomOf;
    }
}
