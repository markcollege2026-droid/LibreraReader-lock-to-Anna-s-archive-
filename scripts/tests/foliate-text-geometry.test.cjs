// Exercise the production bridge with deterministic DOM Range geometry. No browser/downloads needed.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const source = fs.readFileSync(path.join(__dirname,
    '../../app/src/main/assets/foliate/librera-bridge.js'), 'utf8')
    .replace("import './foliate/view.js'", '')
    .replace('window.librera.ready()', '')

const rect = (left, top, right, bottom) => ({ left, top, right, bottom })

function bridge(text, characters, left = 0, extraRects = []) {
    const body = { nodeType: 1, nodeName: 'BODY' }
    const node = { nodeType: 3, nodeValue: text, parentNode: body }
    const doc = {
        body,
        contains: () => true,
        createTreeWalker() {
            let visited = false
            return { nextNode() { if (visited) return null; visited = true; return node } }
        },
        createRange() {
            let from = 0, to = text.length
            const getClientRects = () => {
                const result = [...extraRects]
                for (const box of characters.slice(from, to)) {
                    const previous = result[result.length - 1]
                    if (previous && previous.top === box.top && previous.right === box.left) {
                        previous.right = box.right
                    } else result.push({ ...box })
                }
                return result
            }
            return {
                setStart: (_, value) => { from = value },
                setEnd: (_, value) => { to = value },
                selectNodeContents: () => { from = 0; to = text.length },
                getClientRects,
                getBoundingClientRect() {
                    const boxes = getClientRects()
                    return rect(Math.min(...boxes.map(b => b.left)), Math.min(...boxes.map(b => b.top)),
                        Math.max(...boxes.map(b => b.right)), Math.max(...boxes.map(b => b.bottom)))
                },
                detach() {},
            }
        },
        defaultView: {
            frameElement: { getBoundingClientRect: () => ({ left, top: 0 }) },
            getComputedStyle: () => ({ display: 'block', fontSize: '16px' }),
        },
    }
    body.ownerDocument = doc
    const context = vm.createContext({
        window: { addEventListener() {}, devicePixelRatio: 1 },
        document: { documentElement: { clientWidth: 100, clientHeight: 100 } },
        Node: { ELEMENT_NODE: 1, TEXT_NODE: 3 },
        NodeFilter: { SHOW_TEXT: 4, FILTER_ACCEPT: 1, FILTER_REJECT: 2 },
        doc,
    })
    vm.runInContext(source + `
        view = { renderer: { getContents: () => [{ doc }] } };
        globalThis.probe = { collectWords, collectText, normalize, sameLine };
    `, context)
    return context.probe
}

const text = 'some\u00adthing at'
const characters = [
    ...Array.from({ length: 4 }, (_, i) => rect(80 + i * 5, 80, 85 + i * 5, 90)),
    rect(100, 80, 100, 90), // invisible soft hyphen / caret, not a painted fragment
    ...Array.from({ length: 8 }, (_, i) => rect(110 + i * 5, 0, 115 + i * 5, 10)),
]

for (const [name, offset, expected] of [['previous column', 0, 'some'], ['next column', -110, 'thing at']]) {
    test(`a soft-hyphenated word crossing a column: ${name}`, () => {
        const probe = bridge(text, characters, offset)
        assert.equal(probe.collectText().text, expected)
        assert.equal(probe.collectWords().words.map(w => w.w).join(''), expected)
        for (const word of probe.collectWords().words) {
            assert.ok(word.l < word.r && word.t < word.b, 'a word has painted area')
        }
    })
}

test('adjacent pages preserve every source letter once, independently of TTS output', () => {
    const selected = [0, -110].map(offset => bridge(text, characters, offset).collectWords()
        .words.map(word => word.w).join('')).join('')
    assert.equal(selected.replace(/\s/g, ''), 'somethingat')
})

test('a zero-width caret on this page cannot bring in a word from the next column', () => {
    const probe = bridge('at', [rect(110, 0, 115, 10), rect(115, 0, 120, 10)],
        0, [rect(80, 90, 80, 100)])
    assert.equal(probe.collectWords().words.length, 0)
    assert.equal(probe.collectText().text, '')
})

test('equal baselines in different columns are not one line', () => {
    const probe = bridge(text, characters)
    assert.equal(probe.sameLine(rect(10, 20, 20, 30), rect(110, 20, 120, 30)), false)
    assert.equal(probe.sameLine(rect(10, 20, 20, 30), rect(20, 20, 25, 30)), true)
})

test('normalization rejects off-page and zero-area rectangles on both axes', () => {
    const probe = bridge(text, characters)
    const offset = { left: 0, top: 0 }, size = { width: 100, height: 100 }
    for (const box of [rect(10, 110, 20, 120), rect(10, -20, 20, -10),
        rect(110, 10, 120, 20), rect(10, 10, 10, 20)]) {
        assert.equal(probe.normalize(box, offset, size), null)
    }
    assert.notEqual(probe.normalize(rect(10, 10, 20, 20), offset, size), null)
})
