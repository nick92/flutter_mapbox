import Foundation
import MapboxMaps

public class MapboxPointAnnotation
{
    let id: String
    let name: String
    let annotation: [PointAnnotation]

    public init(id: String, name: String, annotation: [PointAnnotation]) {
        self.id = id
        self.name = name
        self.annotation = annotation
    }
}
