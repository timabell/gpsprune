package tim.prune.gui.map;

import java.awt.Image;

/**
 * Class to act as a memory-based map tile cache
 * For caching of tiles on disk, see the DiskTileCacher class.
 */
public class MemTileCacher
{
	/** Array of images to hold tiles */
	private Image[] _tiles = new Image[GRID_SIZE * GRID_SIZE];
	/** Current zoom level */
	private int _zoom = -1;
	/** X coordinate of central tile */
	private int _tileX = -1;
	/** Y coordinate of central tile */
	private int _tileY = -1;
	/** X coord of grid centre */
	private int _gridCentreX = 0;
	/** Y coord of grid centre */
	private int _gridCentreY = 0;

	/** Grid size */
	private static final int GRID_SIZE = 15;

	/**
	 * Recentre the map and clear the cache
	 * @param inZoom zoom level
	 * @param inTileX x coord of central tile
	 * @param inTileY y coord of central tile
	 */
	public void centreMap(int inZoom, int inTileX, int inTileY)
	{
		int shift = Math.max(Math.abs(inTileX-_tileX), Math.abs(inTileY - _tileY));
		if (shift == 0) {return;}
		// Clear cache if either zoom has changed or map has jumped too far
		if (inZoom != _zoom || shift > GRID_SIZE/2)
		{
			_zoom = inZoom;
			clearAll();
		}
		_gridCentreX = getCacheCoordinate(_gridCentreX + inTileX - _tileX);
		_gridCentreY = getCacheCoordinate(_gridCentreY + inTileY - _tileY);
		_tileX = inTileX;
		_tileY = inTileY;
		// Mark boundaries as invalid
		for (int i=0; i<GRID_SIZE; i++)
		{
			_tiles[getArrayIndex(_tileX + GRID_SIZE/2 + 1, _tileY + i - GRID_SIZE/2)] = null;
			_tiles[getArrayIndex(_tileX + i - GRID_SIZE/2, _tileY + GRID_SIZE/2 + 1)] = null;
		}
	}

	/**
	 * Transform a coordinate from map tiles to array coordinates
	 * @param inTile coordinate of tile
	 * @return coordinate in array (wrapping around cache grid)
	 */
	private static int getCacheCoordinate(int inTile)
	{
		int tile = inTile;
		while (tile >= GRID_SIZE) {tile -= GRID_SIZE;}
		while (tile < 0) {tile += GRID_SIZE;}
		return tile;
	}

	/**
	 * Get the array index for the given coordinates
	 * @param inX x coord of tile
	 * @param inY y coord of tile
	 * @return array index
	 */
	private int getArrayIndex(int inX, int inY)
	{
		//System.out.println("Getting array index for (" + inX + ", " + inY + ") where the centre is at ("  + _tileX + ", " + _tileY
		//	+ ") and grid coords (" + _gridCentreX + ", " + _gridCentreY + ")");
		int x = getCacheCoordinate(inX - _tileX + _gridCentreX);
		int y = getCacheCoordinate(inY - _tileY + _gridCentreY);
		//System.out.println("Transformed to (" + x + ", " + y + ")");
		return (x + y * GRID_SIZE);
	}

	/**
	 * Clear all the cached images
	 */
	public void clearAll()
	{
		// Clear all images if zoom changed
		for (int i=0; i<_tiles.length; i++) {
			_tiles[i] = null;
		}
	}

	/**
	 * @param inX x index of tile
	 * @param inY y index of tile
	 * @return selected tile if already loaded, or null otherwise
	 */
	public Image getTile(int inX, int inY)
	{
		return _tiles[getArrayIndex(inX, inY)];
	}

	/**
	 * Save the specified tile at the given coordinates
	 * @param inTile image to save
	 * @param inX x coordinate of tile
	 * @param inY y coordinate of tile
	 */
	public void setTile(Image inTile, int inX, int inY)
	{
		_tiles[getArrayIndex(inX, inY)] = inTile;
	}
}
