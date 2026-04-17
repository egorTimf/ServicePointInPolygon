using NetTopologySuite.Geometries;

namespace PointInPolygonTest.Geometry
{
    public static class PointInPolygon
{
    public static bool Contains(Polygon polygon, Point p)
    {
        var coord = new Coordinate(p.Coordinate.X,  p.Coordinate.Y);
        
        if (!polygon.EnvelopeInternal.Contains(coord))
            return false;
        
        var pointInsideHole = polygon.Holes.Any(hole => Winding(hole.Coordinates, coord, true));
        if (pointInsideHole) return false;
        
        var windingExterior = Winding(polygon.ExteriorRing.Coordinates, coord, false);
        
        
        return windingExterior;
    }

    // -------------------------
    // 1. WINDING (non-zero rule)
    // -------------------------
    private static bool Winding(Coordinate[] ring, Coordinate p, bool isHole)
    {
        var wn = 0;
        var n = ring.Length;

        for (var i = 0; i < n; i++)
        {
            var a = ring[i];
            var b = ring[(i + 1) % n];

            if (IsSamePoint(a, p) || IsSamePoint(b, p) || OnSegment(a, b, p)) return !isHole;

            //пропускаем горизонтальные ребра
            if (Math.Abs(a.Y - b.Y) < MathConstants.E) continue;
            
            if (a.Y <= p.Y)
            {
                if (b.Y > p.Y && IsLeft(a, b, p))
                    wn++;
            }
            else
            {
                if (b.Y <= p.Y && !IsLeft(a, b, p))
                    wn--;
            }
            Console.WriteLine(wn);
        }

        return wn != 0;
    }
    //если площадь параллелограма на двух векторах меньше нуля значит угол между ними больше 180
    private static bool IsLeft(Coordinate a, Coordinate b, Coordinate p)
    {
        var ab = new Coordinate(b.X - a.X, b.Y - a.Y);
        var ap = new Coordinate(p.X - a.X, p.Y - a.Y);
        
        return Cross(ab, ap) > 0;
    }

    private static bool IsSamePoint(Coordinate a, Coordinate b)
    {
        return Math.Abs(a.X - b.X) < MathConstants.E && Math.Abs(a.Y - b.Y) <  MathConstants.E;
    }

    private static bool OnSegment(Coordinate a, Coordinate b, Coordinate p)
    {
        var ab = new Coordinate(b.X - a.X, b.Y - a.Y);
        var ap = new Coordinate(p.X - a.X, p.Y - a.Y);
        //если ab и ap не параллельные вектора
        if (Math.Abs(Cross(ab, ap)) > MathConstants.E) return false;
        //ab || ap проверяем лежит ли p в границах отрезка ab
        return p.X >= Math.Min(a.X, b.X) - MathConstants.E && p.X <= Math.Max(a.X, b.X) + MathConstants.E &&
               p.Y >= Math.Min(a.Y, b.Y) - MathConstants.E && p.Y <= Math.Max(a.Y, b.Y) + MathConstants.E;
    }
    
    //векторное произведение
    private static double Cross(Coordinate a, Coordinate b)
    {
        return a.X * b.Y - a.Y * b.X;
    }
}
}