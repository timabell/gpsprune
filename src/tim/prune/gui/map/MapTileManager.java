package tim.prune.gui.map;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.ImageObserver;
import java.net.MalformedURLException;
import java.net.URL;

import tim.prune.config.Config;

/**
 * Class responsible for managing the map tiles,
 * including invoking the correct memory cacher(s) and/or disk cacher(s)
 */
public class MapTileManager implements ImageObserver
{
	/** Parent object to inform when tiles received */
	private MapCanvas _parent = null;
	/** Current map source */
	private MapSource _mapSource = null;
	/** Array of tile caches, one per layer */
	private MemTileCacher[] _tempCaches = null;
	/** Number of layers */
	private int _numLayers = -1;
	/** Current zoom level */
	private int _zoom = 0;


	/**
	 * Constructor
	 * @param inParent parent canvas to be informed of updates
	 */
	public MapTileManager(MapCanvas inParent)
	{
		_parent = inParent;
		// Adjust the index of the selected map source
		adjustSelectedMap();
		resetConfig();
	}

	/**
	 * Recentre the map
	 * @param inZoom zoom level
	 * @param inTileX x coord of central tile
	 * @param inTileY y coord of central tile
	 */
	public void centreMap(int inZoom, int inTileX, int inTileY)
	{
		_zoom = inZoom;
		// Pass params onto all memory cachers
		if (_tempCaches != null) {
			for (int i=0; i<_tempCaches.length; i++) {
				_tempCaches[i].centreMap(inZoom, inTileX, inTileY);
			}
		}
	}

	/**
	 * @return true if zoom is too high for tiles
	 */
	public boolean isOverzoomed()
	{
		// Ask current map source what maximum zoom is
		int maxZoom = (_mapSource == null?0:_mapSource.getMaxZoomLevel());
		return (_zoom > maxZoom);
	}

	/**
	 * Clear all the memory caches due to changed config / zoom
	 */
	public void clearMemoryCaches()
	{
		int numLayers = _mapSource.getNumLayers();
		if (_tempCaches == null || _tempCaches.length != numLayers)
		{
			// Cachers don't match, so need to create the right number of them
			_tempCaches = new MemTileCacher[numLayers];
			for (int i=0; i<numLayers; i++) {
				_tempCaches[i] = new MemTileCacher();
			}
		}
		else {
			// Cachers already there, just need to be cleared
			for (int i=0; i<numLayers; i++) {
				_tempCaches[i].clearAll();
			}
		}
	}

	/**
	 * Reset the map source configuration, apparently it has changed
	 */
	public void resetConfig()
	{
		int sourceNum = Config.getConfigInt(Config.KEY_MAPSOURCE_INDEX);
		_mapSource = MapSourceLibrary.getSource(sourceNum);
		if (_mapSource == null) {_mapSource = MapSourceLibrary.getSource(0);}
		clearMemoryCaches();
		_numLayers = _mapSource.getNumLayers();
	}

	/**
	 * Adjust the index of the selected map
	 * (only required if config was loaded from a previous version of GpsPrune)
	 */
	private void adjustSelectedMap()
	{
		int sourceNum = Config.getConfigInt(Config.KEY_MAPSOURCE_INDEX);
		int prevNumFixed = Config.getConfigInt(Config.KEY_NUM_FIXED_MAPS);
		// Number of fixed maps not specified in version <=13, default to 6
		if (prevNumFixed == 0) prevNumFixed = 6;
		int currNumFixed = MapSourceLibrary.getNumFixedSources();
		// Only need to do something if the number has changed
		if (currNumFixed != prevNumFixed && (sourceNum >= prevNumFixed || sourceNum >= currNumFixed))
		{
			sourceNum += (currNumFixed - prevNumFixed);
			Config.setConfigInt(Config.KEY_MAPSOURCE_INDEX, sourceNum);
		}
		Config.setConfigInt(Config.KEY_NUM_FIXED_MAPS, currNumFixed);
	}

	/**
	 * @return the number of layers in the map
	 */
	public int getNumLayers()
	{
		return _numLayers;
	}

	/**
	 * @param inLayer layer number, starting from 0
	 * @param inX x index of tile
	 * @param inY y index of tile
	 * @return selected tile if already loaded, or null otherwise
	 */
	public Image getTile(int inLayer, int inX, int inY)
	{
		// Check first in memory cache for tile
		MemTileCacher tempCache = _tempCaches[inLayer]; // Should probably guard against nulls and array indexes here
		Image tile = tempCache.getTile(inX, inY);
		if (tile != null) {
			return tile;
		}

		// Tile wasn't in memory, but maybe it's in disk cache (if there is one)
		String diskCachePath = Config.getConfigString(Config.KEY_DISK_CACHE);
		boolean useDisk = (diskCachePath != null);
		boolean onlineMode = Config.getConfigBoolean(Config.KEY_ONLINE_MODE);
		if (useDisk)
		{
			tile = DiskTileCacher.getTile(diskCachePath, _mapSource.makeFilePath(inLayer, _zoom, inX, inY), onlineMode);
			if (tile != null)
			{
				// Pass tile to memory cache
				tempCache.setTile(tile, inX, inY);
				if (tile.getWidth(this) > 0) {return tile;}
				return null;
			}
		}
		// Tile wasn't in memory or on disk, so if online let's get it
		if (onlineMode)
		{
			try
			{
				URL tileUrl = new URL(_mapSource.makeURL(inLayer, _zoom, inX, inY));
				if (useDisk && DiskTileCacher.saveTile(tileUrl, diskCachePath,
					_mapSource.makeFilePath(inLayer, _zoom, inX, inY), this))
				{
					// Image now copied directly from URL stream to disk cache
				}
				else
				{
					// Load image asynchronously, using observer
					tile = Toolkit.getDefaultToolkit().createImage(tileUrl);
					// Pass to memory cache
					_tempCaches[inLayer].setTile(tile, inX, inY);
					if (tile.getWidth(this) > 0) {return tile;}
				}
			}
			catch (MalformedURLException urle) {} // ignore
		}
		return null;
	}

	/**
	 * Method called by image loader to inform of updates to the tiles
	 * @param img the image
	 * @param infoflags flags describing how much of the image is known
	 * @param x ignored
	 * @param y ignored
	 * @param width ignored
	 * @param height ignored
	 * @return false to carry on loading, true to stop
	 */
	public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height)
	{
		boolean loaded = (infoflags & ImageObserver.ALLBITS) > 0;
		boolean error = (infoflags & ImageObserver.ERROR) > 0;
		if (loaded || error) {
			_parent.tilesUpdated(loaded);
		}
		return !loaded;
	}
}
