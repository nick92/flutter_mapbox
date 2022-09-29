///A Geo-coordinate Point used for navigation.
class Annotation {
  String? id;
  double? geometery;
  List<String>? userInfo;

  Annotation(
      {required this.id, required this.geometery, required this.userInfo});

  @override
  String toString() {
    return 'Annotation{id: $id, geometery: $geometery}';
  }
}
