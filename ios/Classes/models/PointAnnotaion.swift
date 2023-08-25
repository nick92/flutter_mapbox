import Foundation
import MapboxMaps

public class MapboxPointAnnotation
{
    let name: String
    let annotation: [PointAnnotation]

    public init(name: String, annotation: [PointAnnotation]) {
        self.name = name
        self.annotation = annotation
    }
}
