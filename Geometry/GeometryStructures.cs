namespace PointInPolygonTest.Geometry;
using NetTopologySuite.Geometries;

public struct RectangleAABB
{
    public double MinX, MinY, MaxX, MaxY;

    public bool Contains(Point p)
    {
        return p.X - MinX >= -MathConstants.E && p.X - MaxX  <= MathConstants.E  &&
               p.Y - MinY >= -MathConstants.E && p.Y - MaxY <= MathConstants.E;
    }
}


