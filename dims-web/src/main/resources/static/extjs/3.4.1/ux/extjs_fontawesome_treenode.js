Ext.tree.TreeNode.prototype.render = Ext.createSequence(Ext.tree.TreeNode.prototype.render, function (treenode) {
  $('.x-tree-node-icon').removeClass('x-tree-node-icon').replaceWith(function () {
    if (typeof($(this).attr('class')) != 'undefined')
      return '<i class="' + $(this).attr('class') + '"/>';
    else
      $(this).addClass('.x-tree-node-icon');
  });
});

Ext.tree.TreeNode.prototype.setIconCls = Ext.createInterceptor(Ext.tree.TreeNode.prototype.setIconCls, function (cls) {
  $($(this)[0].ui.elNode).find('i').removeClass().addClass(cls);
}, Ext.tree.TreeNode.prototype);