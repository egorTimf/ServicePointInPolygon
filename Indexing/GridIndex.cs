using NetTopologySuite.Geometries;
using PointInPolygonTest.Geometry;

namespace PointInPolygonTest.Indexing;

public class GridIndex(double cellSize)
{
    private readonly Dictionary<(int, int), List<Polygon>> _grid = new();

    public Dictionary<(int, int), List<Polygon>> GetGridDictionary() => _grid;
    private (int, int) GetCell(Point p)
    {
        return ((int)Math.Floor(p.X / cellSize),
            (int)Math.Floor(p.Y / cellSize));
    }

    public void Insert(Polygon polygon)
    {
        var box = polygon.EnvelopeInternal;

        var minX = (int)Math.Floor(box.MinX / cellSize);
        var maxX = (int)Math.Floor(box.MaxX / cellSize);
        var minY = (int)Math.Floor(box.MinY / cellSize);
        var maxY = (int)Math.Floor(box.MaxY / cellSize);
        Console.WriteLine($"{minX},{minY},{maxX},{maxY}");
        for (var x = minX; x <= maxX; x++)
        {
            for (var y = minY; y <= maxY; y++)
            {
                var key = (x, y);

                if (!_grid.ContainsKey(key))
                    _grid[key] = [];

                _grid[key].Add(polygon);
            }
        }
    }

    public bool Contains(Point p)
    {
        var key = GetCell(p);

        if (!_grid.TryGetValue(key, out var candidates))
            return false;

        return candidates.Any(polygon => PointInPolygon.Contains(polygon, p));
    }
}