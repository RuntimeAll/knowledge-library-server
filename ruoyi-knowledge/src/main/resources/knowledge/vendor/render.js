/* Offline renderer. User HTML does not provide executable scripts. */
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-latex]').forEach(function (element) {
    katex.render(element.getAttribute('data-latex'), element, {
      throwOnError: false,
      trust: false,
      displayMode: element.getAttribute('data-display') === 'block'
    });
  });
  renderMathInElement(document.body, {
    throwOnError: false,
    trust: false,
    delimiters: [
      { left: '$$', right: '$$', display: true },
      { left: '\\[', right: '\\]', display: true },
      { left: '\\(', right: '\\)', display: false },
      { left: '$', right: '$', display: false }
    ]
  });
});
