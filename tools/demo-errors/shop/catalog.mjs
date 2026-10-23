// A category that lists itself as its own parent sends this walk into endless recursion.
function breadcrumbTrail(category, categories) {
  const parent = categories.get(category.parentId);
  return parent ? [...breadcrumbTrail(parent, categories), category.name] : [category.name];
}

export function categoryPath(categoryId) {
  const categories = new Map([["outdoor", { id: "outdoor", name: "Outdoor", parentId: "outdoor" }]]);
  return breadcrumbTrail(categories.get(categoryId), categories);
}
